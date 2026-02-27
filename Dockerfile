# Multi-stage Dockerfile for Spring Boot application
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install build dependencies
RUN apk add --no-cache bash

WORKDIR /build

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./

# Copy all module build files for dependency resolution
COPY platform-common/build.gradle.kts ./platform-common/
COPY platform-data/build.gradle.kts ./platform-data/
COPY platform-web/build.gradle.kts ./platform-web/
COPY sample-service/build.gradle.kts ./sample-service/

# Download dependencies (layer caching optimization)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY platform-common ./platform-common
COPY platform-data ./platform-data
COPY platform-web ./platform-web
COPY sample-service ./sample-service

# Build the application
RUN ./gradlew :sample-service:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Install necessary runtime dependencies (wget for healthcheck)
RUN apk add --no-cache tzdata wget

# Set timezone
ENV TZ=UTC

WORKDIR /app

# Copy the built JAR from builder stage (exclude -plain.jar)
COPY --from=builder /build/sample-service/build/libs/sample-service-*.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080

# Health check (using root endpoint since actuator is not included)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# Run the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

