# We bypass the compilation stage entirely to avoid the Docker DNS crash
FROM eclipse-temurin:25.0.3_9-jre-alpine

WORKDIR /app

# Safely copy the pre-compiled JAR from your local target folder
COPY target/ai-agent-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8082

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]