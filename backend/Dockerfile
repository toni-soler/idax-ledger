FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
ARG IDAX_CORE_REPOSITORY_URL=https://toni-soler.github.io/idax-core-runtime/maven2
ENV IDAX_CORE_REPOSITORY_URL=$IDAX_CORE_REPOSITORY_URL
WORKDIR /workspace
COPY pom.xml ./
COPY .mvn/docker-local-settings.xml ./.mvn/docker-local-settings.xml
COPY src ./src
RUN mvn -B -U -s .mvn/docker-local-settings.xml -DskipTests package -Didax-core.repository.url="$IDAX_CORE_REPOSITORY_URL"

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/target/idax-ledger-backend-0.2.0.jar app.jar
EXPOSE 8094
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
