FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ai-agent-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
