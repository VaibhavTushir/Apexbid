# For local development and testing purposes only. Not for production use.
FROM eclipse-temurin:21-jre-alpine
EXPOSE 8080
RUN mkdir /app

# Copies the JAR compiled directly on your host machine
COPY build/libs/*.jar /app/apexbid.jar

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/apexbid.jar"]


# PRODUCTION DEPLOYMENT (Multi-Stage)
# To use this: Comment out PATH 1 aboENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/apexbid.jar"] exists.

# Build stage
# FROM gradle:jdk21 AS build
# COPY --chown=gradle:gradle . /home/gradle/src
# WORKDIR /home/gradle/src
# RUN gradle build -x test --no-daemon

# Runtime stage
# FROM eclipse-temurin:21-jre-alpine
# EXPOSE 8080
# RUN mkdir /app
# COPY --from=build /home/gradle/src/build/libs/*.jar /app/apexbid.jar
# ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/apexbid.jar"]