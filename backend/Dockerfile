FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/idax-ledger-backend-0.1.0.jar app.jar
EXPOSE 8094
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
