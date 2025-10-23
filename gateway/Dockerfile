FROM eclipse-temurin:21-jdk-alpine AS dependencies
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline -B

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY --from=dependencies /root/.m2 /root/.m2
COPY --from=dependencies /app/.mvn .mvn
COPY --from=dependencies /app/mvnw .

COPY pom.xml .
COPY src src

RUN chmod +x mvnw && \
    ./mvnw clean package -DskipTests -B && \
    mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=build --chown=spring:spring /app/target/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /app/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /app/target/extracted/application/ ./

USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=70.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

LABEL maintainer="Hyusein Leshov" \
      service="gateway" \
      version="1.0.0" \
      description="API Gateway with JWT authentication and audit logging"
