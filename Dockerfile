# Build: maven:3.9-eclipse-temurin-21 (full JDK) → Runtime: eclipse-temurin:21-jre-alpine (slim)
# One Dockerfile for all modules; pick the service with --build-arg SERVICE=<module>.
# Example: docker build --build-arg SERVICE=user-service -t user-service .
ARG SERVICE=user-service

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /app

# Copy POMs first so dependency resolution is cached unless POMs change
COPY pom.xml ./
COPY user-service/pom.xml user-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY service-discovery/pom.xml service-discovery/pom.xml
RUN mvn -B -q -DskipTests dependency:go-offline -pl ${SERVICE} -am

# Copy sources and build the service jar (tests run in CI, not in the image).
# NOTE: excludes the *.jar.original left behind by spring-boot:repackage
COPY . .
RUN mvn -B -q -DskipTests package -pl ${SERVICE} -am \
    && JAR=$(find ${SERVICE}/target -maxdepth 1 -name "${SERVICE}-*.jar" ! -name "*.original" | head -1) \
    && test -n "$JAR" && cp "$JAR" /app.jar && ls -la /app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE
# Non-root user + wget for compose healthchecks (busybox, no extra layer)
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app
ENV SERVICE_NAME=${SERVICE}
EXPOSE 8080 8081 8082 8083 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
