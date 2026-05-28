FROM eclipse-temurin:25-jdk-alpine

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Build dependencies (this allows Docker to cache the layer)
RUN chmod +x ./mvnw && ./mvnw dependency:resolve

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests

# Create final image
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=0 /app/target/SwiftPay-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

