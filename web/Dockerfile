# Two targets: "dev" runs the Vite dev server with live reload, "prod" serves
# the built app over HTTPS from Caddy. docker-compose.yml picks one per profile.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci


FROM deps AS dev
COPY . .
CMD ["npm", "run", "dev"]


FROM deps AS build
COPY . .
RUN npm run build


FROM caddy:2-alpine AS prod
# Caddy fills in {$SITE_ADDRESS} and {$API_TARGET} from the environment when
# it starts, and handles HTTPS certificates itself.
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
