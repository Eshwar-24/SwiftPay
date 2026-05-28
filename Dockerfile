FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Build dependencies (this allows Docker to cache the layer)
# RUN chmod +x ./mvnw && ./mvnw dependency:resolve -U
RUN chmod +x ./mvnw && \
    for attempt in 1 2 3; do \
        echo "Dependency resolution attempt $attempt..."; \
        ./mvnw --settings .mvn/settings.xml dependency:resolve -DskipTests && break; \
        if [ $attempt -lt 3 ]; then \
            sleep $((attempt * 10)); \
        else \
            echo "Dependency resolution failed after 3 attempts"; \
            exit 1; \
        fi; \
    done

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests

# Create final image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=0 /app/target/SwiftPay-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

