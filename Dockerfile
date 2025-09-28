ENTRYPOINT ["java", "-jar", "app.jar"]

# Commit 5: Enhanced version (when you have time)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]