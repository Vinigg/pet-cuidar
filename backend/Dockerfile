# --- build: gera o JAR com o Maven (testes rodam no CI, nao aqui) ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- runtime: so a JRE + o JAR ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/pet-cuidar-backend-*.jar app.jar
# O SQLite fica num volume em /data para sobreviver a recriacao do container.
RUN mkdir -p /data
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/data/petcuidar.db
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
