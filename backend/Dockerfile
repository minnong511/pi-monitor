FROM eclipse-temurin:25 AS builder

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -DskipTests clean package


FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder \
    /workspace/target/rasp_monitor-0.0.1-SNAPSHOT.jar \
    app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]