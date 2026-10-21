# The Discord bot. Keeps no local state -- everything lives on the hub -- so the container can be
# rebuilt or restarted at any time. Built by deploy/docker/docker-compose.yml.
FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY src ./src
COPY locales ./locales
USER node
CMD ["node", "src/index.js"]
