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
const mandiOptionsCache = new Map();

const asyncRoute = fn => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const auth = (req, res, next) => {
  try { req.user = jwt.verify((req.headers.authorization || '').replace('Bearer ', ''), jwtSecret); next(); }
  catch { res.status(401).json({ error: 'Authentication required' }); }
};
const requireRole = (...roles) => (req, res, next) => roles.includes(req.user.role) ? next() : res.status(403).json({ error: 'Insufficient role' });

app.get('/health', asyncRoute(async (_req, res) => {
  await pool.query('SELECT 1'); res.json({ ok: true, service: 'agritech-api' });
}));

app.get('/mandi/options', asyncRoute(async (req, res) => {
  const apiKey = process.env.DATA_GOV_IN_API_KEY;
  if (!apiKey) return res.status(503).json({ error: 'Official mandi-price API key is not configured', source: 'data.gov.in' });
  const state = String(req.query.state || '').trim();
  if (!state) return res.status(400).json({ error: 'state is required' });
  const cacheKey = state.toLowerCase();
  const cached = mandiOptionsCache.get(cacheKey);
  if (cached && Date.now() - cached.savedAt < 5 * 60 * 1000) {
    res.set('Cache-Control', 'public, max-age=300');
    return res.json(cached.body);
  }
  const url = new URL('https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070');
  url.searchParams.set('api-key', apiKey);
  url.searchParams.set('format', 'json');
  url.searchParams.set('limit', '1000');
  url.searchParams.set('filters[state]', state);
  const upstream = await fetch(url, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(10000) });
  if (!upstream.ok) return res.status(502).json({ error: 'Official mandi-price source unavailable', source: 'data.gov.in' });
  const data = await upstream.json();
  const records = data.records || [];
  const body = {
    state,
    districts: [...new Set(records.map(record => String(record.district || '').trim()).filter(Boolean))].sort(),
    commodities: [...new Set(records.map(record => String(record.commodity || '').trim()).filter(Boolean))].sort(),
    fetchedAt: new Date().toISOString()
  };
  mandiOptionsCache.set(cacheKey, { savedAt: Date.now(), body });
  res.set('Cache-Control', 'public, max-age=300');
  res.json(body);
}));

app.get('/mandi/prices', asyncRoute(async (req, res) => {
  const apiKey = process.env.DATA_GOV_IN_API_KEY;
  if (!apiKey) return res.status(503).json({ error: 'Official mandi-price API key is not configured', source: 'data.gov.in' });
  const url = new URL('https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070');
  url.searchParams.set('api-key', apiKey);
  url.searchParams.set('format', 'json');
  url.searchParams.set('limit', req.query.commodity ? '100' : '1000');
  if (req.query.commodity) url.searchParams.set('filters[commodity]', String(req.query.commodity));
  if (req.query.state) url.searchParams.set('filters[state]', String(req.query.state));
  if (req.query.district) url.searchParams.set('filters[district]', String(req.query.district));
  const upstream = await fetch(url, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(10000) });
  if (!upstream.ok) return res.status(502).json({ error: 'Official mandi-price source unavailable', source: 'data.gov.in' });
  const data = await upstream.json();
  res.set('Cache-Control', 'public, max-age=900');
  res.json({ source: 'AGMARKNET via data.gov.in', fetchedAt: new Date().toISOString(), records: data.records || [] });
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
  const requestedRole = String(req.body.role || 'farmer').trim();
  if (!['farmer', 'buyer', 'processor'].includes(requestedRole)) return res.status(400).json({ error: 'Invalid account role' });
  if (!twilioClient || !process.env.TWILIO_VERIFY_SERVICE_SID) return res.status(503).json({ error: 'OTP service is not configured' });
  const result = await twilioClient.verify.v2.services(process.env.TWILIO_VERIFY_SERVICE_SID).verificationChecks.create({ to: phone, code });
  if (result.status !== 'approved') return res.status(401).json({ error: 'Invalid OTP' });
  const id = crypto.randomUUID();
  const user = (await pool.query(`INSERT INTO users(id, phone, role) VALUES($1,$2,$3) ON CONFLICT(phone) DO UPDATE SET role=EXCLUDED.role RETURNING id,phone,role`, [id, phone, requestedRole])).rows[0];
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

app.get('/chat/messages', auth, asyncRoute(async (req, res) => {
  const otherUserId = String(req.query.otherUserId || '');
  const listingId = req.query.listingId ? String(req.query.listingId) : null;
  if (!otherUserId) return res.status(400).json({ error: 'otherUserId is required' });
  const rows = await pool.query(`SELECT id, listing_id, sender_id, recipient_id, body, created_at
    FROM chat_messages WHERE listing_id IS NOT DISTINCT FROM $3::uuid
    AND ((sender_id=$1 AND recipient_id=$2) OR (sender_id=$2 AND recipient_id=$1))
    ORDER BY created_at ASC LIMIT 300`, [req.user.sub, otherUserId, listingId]);
  res.json(rows.rows);
}));
app.post('/chat/messages', auth, asyncRoute(async (req, res) => {
  const recipientId = String(req.body.otherUserId || '');
  const listingId = req.body.listingId ? String(req.body.listingId) : null;
  const body = String(req.body.body || '').trim();
  if (!recipientId || recipientId === req.user.sub || !body || body.length > 2000) return res.status(400).json({ error: 'Invalid chat message' });
  const recipient = await pool.query('SELECT id FROM users WHERE id=$1', [recipientId]);
  if (!recipient.rowCount) return res.status(404).json({ error: 'Recipient not found' });
  const result = await pool.query(`INSERT INTO chat_messages(id,listing_id,sender_id,recipient_id,body)
    VALUES($1,$2,$3,$4,$5) RETURNING id,listing_id,sender_id,recipient_id,body,created_at`,
    [crypto.randomUUID(), listingId, req.user.sub, recipientId, body]);
  res.status(201).json(result.rows[0]);
}));

const razorpayReady = () => process.env.RAZORPAY_KEY_ID && process.env.RAZORPAY_KEY_SECRET;
const razorpayRequest = async (endpoint, method, body) => {
  const credentials = Buffer.from(`${process.env.RAZORPAY_KEY_ID}:${process.env.RAZORPAY_KEY_SECRET}`).toString('base64');
  const response = await fetch(`https://api.razorpay.com/v1${endpoint}`, {
    method, headers: { Authorization: `Basic ${credentials}`, 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(12000)
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(`Payment gateway error ${response.status}`);
  return payload;
};
app.post('/payments/orders', auth, requireRole('buyer','processor'), asyncRoute(async (req, res) => {
  if (!razorpayReady()) return res.status(503).json({ error: 'Payment gateway is not configured' });
  const quantity = Number(req.body.quantity); const listingId = String(req.body.listingId || '');
  if (!Number.isFinite(quantity) || quantity <= 0) return res.status(400).json({ error: 'Invalid quantity' });
  const listing = await pool.query("SELECT id,farmer_id,quantity,price_per_unit,status FROM listings WHERE id=$1 AND status='active'", [listingId]);
  if (listing.rowCount && listing.rows[0].farmer_id === req.user.sub) return res.status(403).json({ error: 'You cannot buy your own listing' });
  if (!listing.rowCount || quantity > Number(listing.rows[0].quantity)) return res.status(400).json({ error: 'Listing unavailable or requested quantity exceeds supply' });
  const amount = Math.round(Number(listing.rows[0].price_per_unit) * quantity * 100);
  if (amount < 100 || !Number.isSafeInteger(amount)) return res.status(400).json({ error: 'Invalid payment amount' });
  const order = await razorpayRequest('/orders', 'POST', { amount, currency: 'INR', receipt: crypto.randomUUID() });
  await pool.query(`INSERT INTO payments(id,buyer_id,listing_id,quantity,amount_paise,gateway_order_id)
    VALUES($1,$2,$3,$4,$5,$6)`, [crypto.randomUUID(), req.user.sub, listingId, quantity, amount, order.id]);
  res.status(201).json({ keyId: process.env.RAZORPAY_KEY_ID, orderId: order.id, amount: order.amount, currency: order.currency });
}));
app.post('/payments/verify', auth, requireRole('buyer','processor'), asyncRoute(async (req, res) => {
  if (!razorpayReady()) return res.status(503).json({ error: 'Payment gateway is not configured' });
  const { orderId, paymentId, signature } = req.body;
  if (!orderId || !paymentId || !signature) return res.status(400).json({ error: 'Payment verification fields required' });
  const stored = await pool.query('SELECT * FROM payments WHERE gateway_order_id=$1 AND buyer_id=$2', [orderId, req.user.sub]);
  if (!stored.rowCount) return res.status(404).json({ error: 'Order not found' });
  const expected = crypto.createHmac('sha256', process.env.RAZORPAY_KEY_SECRET).update(`${orderId}|${paymentId}`).digest();
  let supplied; try { supplied = Buffer.from(signature, 'hex'); } catch { supplied = Buffer.alloc(0); }
  if (expected.length !== supplied.length || !crypto.timingSafeEqual(expected, supplied)) return res.status(401).json({ error: 'Invalid payment signature' });
  const payment = await razorpayRequest(`/payments/${encodeURIComponent(paymentId)}`, 'GET');
  if (payment.order_id !== orderId || payment.status !== 'captured' || Number(payment.amount) !== Number(stored.rows[0].amount_paise)) return res.status(409).json({ error: 'Payment is not captured for this order' });
  await pool.query("UPDATE payments SET gateway_payment_id=$1,status='paid',paid_at=now() WHERE gateway_order_id=$2", [paymentId, orderId]);
  res.json({ ok: true, status: 'paid', orderId, paymentId });
}));

app.use((err, _req, res, _next) => { console.error(err); res.status(500).json({ error: 'Internal server error' }); });
const port = process.env.PORT || 3000;
const start = async () => {
  if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
  const schemaPath = path.join(path.dirname(new URL(import.meta.url).pathname), '../schema.sql');
  await pool.query(fs.readFileSync(schemaPath, 'utf8'));
  app.listen(port, () => console.log(`AgriTech API listening on ${port}`));
};
start().catch(error => { console.error(error); process.exit(1); });
