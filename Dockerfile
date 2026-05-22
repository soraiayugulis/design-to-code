FROM openjdk:21-jdk-slim as builder

WORKDIR /build

COPY build.gradle.kts settings.gradle.kts ./
COPY gradlew gradlew.bat ./
COPY gradle ./gradle

RUN ./gradlew build --no-daemon

FROM openjdk:21-jre-slim

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar design-to-code.jar

ENTRYPOINT ["java", "-jar", "design-to-code.jar"]
