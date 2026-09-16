import express from 'express';
import cors from 'cors';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import jwt from 'jsonwebtoken';
import twilio from 'twilio';
import pg from 'pg';

const { Pool } = pg;
const app = express();
app.use(cors());
app.use(express.json({ limit: '1mb' }));
const pool = new Pool({ connectionString: process.env.DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false });
const jwtSecret = process.env.JWT_SECRET;
if (!jwtSecret) console.warn('JWT_SECRET is not configured');
const twilioClient = process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN
  ? twilio(process.env.TWILIO_ACCOUNT_SID, process.env.TWILIO_AUTH_TOKEN) : null;

const asyncRoute = fn => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const auth = (req, res, next) => {
  try { req.user = jwt.verify((req.headers.authorization || '').replace('Bearer ', ''), jwtSecret); next(); }
  catch { res.status(401).json({ error: 'Authentication required' }); }
};
const requireRole = (...roles) => (req, res, next) => roles.includes(req.user.role) ? next() : res.status(403).json({ error: 'Insufficient role' });

app.get('/health', asyncRoute(async (_req, res) => {
  await pool.query('SELECT 1'); res.json({ ok: true, service: 'agritech-api' });
}));

app.post('/auth/request-otp', asyncRoute(async (req, res) => {
  const phone = String(req.body.phone || '').trim();
  if (!/^\+[1-9]\d{7,14}$/.test(phone)) return res.status(400).json({ error: 'Use E.164 phone format' });
  if (!twilioClient || !process.env.TWILIO_VERIFY_SERVICE_SID) return res.status(503).json({ error: 'OTP service is not configured' });
  await twilioClient.verify.v2.services(process.env.TWILIO_VERIFY_SERVICE_SID).verifications.create({ to: phone, channel: 'sms' });
  res.json({ ok: true });
}));

app.post('/auth/verify-otp', asyncRoute(async (req, res) => {
  const phone = String(req.body.phone || '').trim(); const code = String(req.body.code || '').trim();
  if (!twilioClient || !process.env.TWILIO_VERIFY_SERVICE_SID) return res.status(503).json({ error: 'OTP service is not configured' });
  const result = await twilioClient.verify.v2.services(process.env.TWILIO_VERIFY_SERVICE_SID).verificationChecks.create({ to: phone, code });
  if (result.status !== 'approved') return res.status(401).json({ error: 'Invalid OTP' });
  const id = crypto.randomUUID();
  const user = (await pool.query(`INSERT INTO users(id, phone) VALUES($1,$2) ON CONFLICT(phone) DO UPDATE SET phone=EXCLUDED.phone RETURNING id,phone,role`, [id, phone])).rows[0];
  res.json({ token: jwt.sign({ sub: user.id, phone: user.phone, role: user.role }, jwtSecret, { expiresIn: '30d' }), user });
}));

app.get('/listings', asyncRoute(async (req, res) => {
  const values = []; const where = ["status='active'"];
  if (req.query.state) { values.push(req.query.state); where.push(`state=$${values.length}`); }
  if (req.query.crop) { values.push(req.query.crop); where.push(`crop_name=$${values.length}`); }
  res.json((await pool.query(`SELECT * FROM listings WHERE ${where.join(' AND ')} ORDER BY created_at DESC LIMIT 200`, values)).rows);
}));
app.post('/listings', auth, requireRole('farmer'), asyncRoute(async (req, res) => {
  const { cropName, quantity, unit = 'quintal', quality, pricePerUnit, location, state, description } = req.body;
  if (!cropName || !Number.isFinite(+quantity) || !Number.isFinite(+pricePerUnit)) return res.status(400).json({ error: 'Invalid listing' });
  const row = (await pool.query(`INSERT INTO listings(id,farmer_id,crop_name,quantity,unit,quality,price_per_unit,location,state,description) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`, [crypto.randomUUID(), req.user.sub, cropName, quantity, unit, quality, pricePerUnit, location, state, description])).rows[0];
  res.status(201).json(row);
}));
app.get('/buy-requests', auth, asyncRoute(async (_req, res) => res.json((await pool.query("SELECT * FROM buy_requests WHERE status='open' ORDER BY created_at DESC LIMIT 200")).rows)));
app.post('/buy-requests', auth, requireRole('buyer','processor'), asyncRoute(async (req, res) => {
  const { cropName, quantity, unit = 'quintal', maxPricePerUnit, location, state } = req.body;
  if (!cropName || !Number.isFinite(+quantity) || !Number.isFinite(+maxPricePerUnit)) return res.status(400).json({ error: 'Invalid buy request' });
  res.status(201).json((await pool.query(`INSERT INTO buy_requests(id,buyer_id,crop_name,quantity,unit,max_price_per_unit,location,state) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`, [crypto.randomUUID(), req.user.sub, cropName, quantity, unit, maxPricePerUnit, location, state])).rows[0]);
}));
app.get('/cold-storage/lots', auth, asyncRoute(async (req, res) => res.json((await pool.query('SELECT * FROM cold_storage_lots WHERE owner_id=$1 ORDER BY updated_at DESC', [req.user.sub])).rows)));
app.post('/cold-storage/lots', auth, asyncRoute(async (req, res) => {
  const { cropName, quantity, unit = 'quintal', storageName, chamberCode, temperatureC, status = 'stored' } = req.body;
  if (!cropName || !storageName || !Number.isFinite(+quantity)) return res.status(400).json({ error: 'Invalid storage lot' });
  res.status(201).json((await pool.query(`INSERT INTO cold_storage_lots(id,owner_id,crop_name,quantity,unit,storage_name,chamber_code,temperature_c,status) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`, [crypto.randomUUID(), req.user.sub, cropName, quantity, unit, storageName, chamberCode, temperatureC, status])).rows[0]);
}));
app.post('/cold-storage/lots/:lotId/events', auth, asyncRoute(async (req, res) => {
  const lot = await pool.query('SELECT id FROM cold_storage_lots WHERE id=$1 AND owner_id=$2', [req.params.lotId, req.user.sub]);
  if (!lot.rowCount) return res.status(404).json({ error: 'Lot not found' });
  res.status(201).json((await pool.query(`INSERT INTO supply_chain_events(id,lot_id,event_type,note,latitude,longitude) VALUES($1,$2,$3,$4,$5,$6) RETURNING *`, [crypto.randomUUID(), req.params.lotId, req.body.eventType, req.body.note, req.body.latitude, req.body.longitude])).rows[0]);
}));

app.use((err, _req, res, _next) => { console.error(err); res.status(500).json({ error: 'Internal server error' }); });
const port = process.env.PORT || 3000;
const start = async () => {
  if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
  await pool.query(fs.readFileSync(path.join(process.cwd(), 'server/schema.sql'), 'utf8'));
  app.listen(port, () => console.log(`AgriTech API listening on ${port}`));
};
start().catch(error => { console.error(error); process.exit(1); });
