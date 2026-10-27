# The Vite dev server with live reload, for local development. The add-on
# builds the app itself (see ../Dockerfile), and the backend serves it.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci


FROM deps AS dev
COPY . .
CMD ["npm", "run", "dev"]
