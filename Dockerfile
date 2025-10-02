# Multi-stage build for PayLite

# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom and download dependencies first (better caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Stage 2: Development stage (with hot reload)
FROM builder AS development
EXPOSE 8080
# Don't build here - let Maven run with source code mounted
CMD ["mvn", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-Dspring-boot.run.profiles=dev"]

# Stage 3: Production build stage
FROM builder AS production-build
RUN mvn clean package -DskipTests

# Stage 4: Production runtime stage (using compatible base image)
FROM eclipse-temurin:17-jre-jammy AS production
WORKDIR /app

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy built artifact from production-build stage
COPY --from=production-build --chown=spring:spring /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]