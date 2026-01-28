FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew gradle/ build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --no-daemon clean bootJar -x test || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV SPRING_ACTIVE_PROFILES=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
