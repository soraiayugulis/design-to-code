FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

COPY build.gradle.kts settings.gradle.kts ./
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src

RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/build/libs/*.jar design-to-code.jar

ENTRYPOINT ["java", "-jar", "design-to-code.jar"]
