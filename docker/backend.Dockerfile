FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle backend/settings.gradle backend/
COPY backend/src backend/src
WORKDIR /app/backend
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/backend/build/libs/*.jar app.jar
RUN mkdir -p /app/data
VOLUME /app/data
EXPOSE 8081
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
