# --- build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/pet-cuidar-backend-*.jar /app.jar

# --- runtime ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
# O SQLite fica em /data (volume), para sobreviver a rebuilds do container
RUN mkdir -p /data
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/data/petcuidar.db \
    JAVA_OPTS="-Xms128m -Xmx384m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
