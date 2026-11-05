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


# multilingual-e5-small, which sorts items into store sections. The same files
# on every architecture. Pinned to a revision and checked, so a build never
# picks up a different model.
FROM --platform=$BUILDPLATFORM alpine:3 AS model
ARG MODEL_REVISION=761b726dd34fb83930e26aab4e9ac3899aa1fa78
ARG MODEL_URL=https://huggingface.co/Xenova/multilingual-e5-small/resolve/$MODEL_REVISION
RUN apk add --no-cache curl \
    && mkdir /model \
    && curl -sSfL -o /model/model.onnx $MODEL_URL/onnx/model_quantized.onnx \
    && curl -sSfL -o /model/tokenizer.json $MODEL_URL/tokenizer.json \
    && echo "f80102d3f2a1229f387d3c81909990d8945513e347b0eab049f7de3c6f98c193  /model/model.onnx" | sha256sum -c \
    && echo "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39  /model/tokenizer.json" | sha256sum -c


FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=rest /rest/target/dependency/ ./lib/
COPY --from=web /web/dist/ ./web/
COPY --from=model /model/ ./model/

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
     "-DMODEL.DIR=/app/model", \
     "-cp", "/app/lib/*", \
     "no.eirikgg.shoppinglist.Main"]
