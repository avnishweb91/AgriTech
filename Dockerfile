FROM node:20-alpine
WORKDIR /app
COPY server/package*.json ./server/
RUN npm --prefix server install --omit=dev
COPY server ./server
CMD ["npm", "--prefix", "server", "start"]
