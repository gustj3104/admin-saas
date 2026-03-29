FROM gradle:8.10.2-jdk17 AS builder

WORKDIR /app

COPY server/build.gradle ./build.gradle
COPY server/settings.gradle ./settings.gradle
COPY server/gradlew ./gradlew
COPY server/gradlew.bat ./gradlew.bat
COPY server/gradle ./gradle
COPY server/src ./src

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/libs/admin-saas-server-0.0.1-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
