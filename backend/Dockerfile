# ---------- 1) Build: compila o projeto e gera o .jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Baixa as dependências primeiro: esta camada fica em cache enquanto o pom.xml não mudar.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# ---------- 2) Execução: só o Java (JRE) e o .jar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Não roda como root.
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /app/app.jar app.jar

# Pensado para instâncias pequenas (ex.: Render Free, 512 MB):
# limita a memória da JVM a 75% do contêiner e usa um coletor de lixo leve.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

# A hospedagem define PORT; localmente usa 8080 (veja application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
