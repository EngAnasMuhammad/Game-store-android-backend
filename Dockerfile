# Stage 1: Build with Gradle using your exact wrapper & Java 21
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew installDist --no-daemon

# Stage 2: Lightweight Java 21 runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /src/build/install/*/lib/ /app/lib/

EXPOSE 8080

# Headless flag for Thumbnailator (ImageIO) + RAM cap for Render's 512 MB limit
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=75.0", "-cp", "/app/lib/*", "com.anasdev.MainKt"]