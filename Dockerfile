# Stage 1: Build using the latest Gradle for JDK 21
# We use 'gradle:jdk21' to always get the most recent compatible Gradle version
FROM gradle:jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build -x test --no-daemon

# Stage 2: Runtime using JDK 21 (Alpine for small footprint)
FROM eclipse-temurin:21-jre-alpine
EXPOSE 8080
RUN mkdir /app

COPY --from=build /home/gradle/src/build/libs/*.jar /app/apexbid.jar

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/apexbid.jar"]