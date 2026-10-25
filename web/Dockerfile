# Two targets: "dev" runs the Vite dev server with live reload, "prod" serves
# the built app from nginx. docker-compose.yml picks one per profile.

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


FROM nginx:1.27-alpine AS prod
# The nginx image fills in ${WEB_PORT} and ${API_TARGET} from the environment
# when the container starts.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
