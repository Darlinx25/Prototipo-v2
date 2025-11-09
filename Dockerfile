# ETAPA DE CONSTRUCCIÓN (BUILDER)
FROM eclipse-temurin:21-jdk-jammy AS builder

# 1. Instala Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# 2. Define el directorio de trabajo
WORKDIR /app

# 3. Copia los POMs
COPY pom.xml .
COPY control/pom.xml control/
COPY app/pom.xml app/

# 4. Copia el código fuente
COPY control/src control/src
COPY app/src app/src

# 5. Compila e instala TODO el proyecto.
# Esto asegura que los módulos locales (padre, control) se instalen antes que 'app'.
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean install -DskipTests

# ETAPA DE EJECUCIÓN (RUNTIME)
FROM eclipse-temurin:21-jre-jammy AS stage-1

# 1. Define el directorio de trabajo
WORKDIR /app

# 2. Copia el Uber-JAR. 
# ASUMIMOS que el Shade Plugin lo renombró a 'app.jar'.
COPY --from=builder /app/app/target/app.jar ./app.jar

# 3. Comando de ejecución
CMD ["java", "-jar", "app.jar"]