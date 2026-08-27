FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG MODULE
WORKDIR /workspace
COPY backend ./backend
RUN test -n "$MODULE" \
    && mvn -B -f backend/pom.xml -pl "$MODULE" -am -DskipTests clean package \
    && find "backend/$MODULE/target" -maxdepth 1 -type f -name "$MODULE-*.jar" ! -name "*.original" ! -name "*-sources.jar" ! -name "*-javadoc.jar" -exec cp {} /workspace/app.jar \; \
    && test -s /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 firefly \
    && useradd --system --uid 10001 --gid firefly --home-dir /app firefly

WORKDIR /app
COPY --from=build --chown=firefly:firefly /workspace/app.jar ./app.jar
USER firefly

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
