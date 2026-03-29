FROM --platform=linux/amd64 eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon --info bootJar -x test

FROM --platform=linux/amd64 eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV SPRING_ACTIVE_PROFILES=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
