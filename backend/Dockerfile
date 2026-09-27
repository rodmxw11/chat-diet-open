FROM eclipse-temurin:21-jre
WORKDIR /app

# Fixed UID so a Linux host can chown its bind-mounted data/ and certs/ to match.
RUN groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid 10001 --create-home appuser \
    && mkdir -p /app/data && chown appuser:appuser /app /app/data
COPY --chown=appuser:appuser build/libs/*.jar app.jar
USER appuser

VOLUME ["/app/data"]
EXPOSE 8443 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
