ARG RUNTIME_IMAGE=eclipse-temurin:25-jre
FROM ${RUNTIME_IMAGE}

WORKDIR /app

COPY openchat-chat-server-1.0-SNAPSHOT.jar app.jar

EXPOSE 10086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
