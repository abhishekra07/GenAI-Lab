# ==========================================================
# STAGE 1: Build
# ==========================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

# Set working directory inside the build container
WORKDIR /build

# ==========================================================
# DEPENDENCY CACHING OPTIMIZATION
# ==========================================================

# Copy parent POM first
COPY pom.xml .

# Copy all module POMs (maintains module structure for Maven)
COPY genai-lab-common/pom.xml genai-lab-common/
COPY genai-lab-security/pom.xml genai-lab-security/
COPY genai-lab-ai/pom.xml genai-lab-ai/
COPY genai-lab-storage/pom.xml genai-lab-storage/
COPY genai-lab-chat/pom.xml genai-lab-chat/
COPY genai-lab-document/pom.xml genai-lab-document/
COPY genai-lab-rag/pom.xml genai-lab-rag/
COPY genai-lab-metrics/pom.xml genai-lab-metrics/
COPY genai-lab-api/pom.xml genai-lab-api/
COPY genai-lab-app/pom.xml genai-lab-app/

RUN mvn -B -e dependency:go-offline

# ==========================================================
# BUILD THE APPLICATION
# ==========================================================

# Now copy ALL source code (this layer will change with every code change)
COPY genai-lab-common/src genai-lab-common/src
COPY genai-lab-security/src genai-lab-security/src
COPY genai-lab-ai/src genai-lab-ai/src
COPY genai-lab-storage/src genai-lab-storage/src
COPY genai-lab-chat/src genai-lab-chat/src
COPY genai-lab-document/src genai-lab-document/src
COPY genai-lab-rag/src genai-lab-rag/src
COPY genai-lab-metrics/src genai-lab-metrics/src
COPY genai-lab-api/src genai-lab-api/src
COPY genai-lab-app/src genai-lab-app/src

RUN mvn -B -e clean package -Dmaven.test.skip=true

# ==========================================================
# STAGE 2: Runtime Image
# ==========================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for Docker healthcheck (curl is not in alpine by default)
RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/genai-lab-app/target/genai-lab-app-*.jar app.jar

# Change ownership of the app directory to our non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# ==========================================================
# JVM CONFIGURATION
# ==========================================================

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

# The port our Spring Boot app listens on (matches server.port in application.yml)
EXPOSE 8080

# ==========================================================
# STARTUP COMMAND
# ==========================================================
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
