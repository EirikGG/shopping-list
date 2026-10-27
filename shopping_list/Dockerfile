# The add-on image. GitHub Actions builds it for each architecture and
# publishes it (see .github/workflows/addon.yml); Home Assistant downloads it.
#
# The web app and the jar are the same on every architecture, so they are
# built once on the machine running the build ($BUILDPLATFORM). Only the last
# stage is per architecture, and it runs nothing, so no emulation is needed.

FROM --platform=$BUILDPLATFORM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build


FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS rest
WORKDIR /rest
COPY rest/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY rest/src ./src
# The app's jar and every jar it needs at runtime, in target/dependency.
RUN mvn -B -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime \
    && cp target/*.jar target/dependency/


FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=rest /rest/target/dependency/ ./lib/
COPY --from=web /web/dist/ ./web/

# debug would log every SQL query.
ENV LOG_LEVEL=info \
    LOG_LEVEL_ROOT=info

# UTC, as the web app reads the backend's times as UTC. HA sets TZ to its own
# time zone in add-ons, which would shift them.
CMD ["java", \
     "-Xmx192m", "-XX:+UseSerialGC", \
     "-Duser.timezone=UTC", \
     "-DAPP.PORT=8099", \
     "-DDB.FILE=/data/shoppinglist.db", \
     "-DWEB.DIR=/app/web", \
     "-cp", "/app/lib/*", \
     "no.eirikgg.shoppinglist.Main"]
