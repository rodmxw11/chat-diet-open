FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home appuser
COPY build/libs/*.jar app.jar
RUN mkdir -p /app/data && chown -R appuser:appuser /app
USER appuser

VOLUME ["/app/data"]
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
