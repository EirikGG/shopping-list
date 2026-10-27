# "dev" runs the Vite dev server with live reload; "build" builds the app.
# In Home Assistant the backend serves the built app (see the add-on).

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
