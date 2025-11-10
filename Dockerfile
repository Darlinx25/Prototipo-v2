# ETAPA DE CONSTRUCCIÓN (BUILDER)
# Usa el JDK para compilar el código fuente y Maven
FROM eclipse-temurin:21-jdk-jammy AS builder

# 1. Instala Maven y otras herramientas necesarias
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# 2. Define el directorio de trabajo
WORKDIR /app

# 3. Copia el POM padre y los sub-POMs primero para aprovechar el caché
# Esto evita re-descargar dependencias si solo cambian las clases fuente
COPY pom.xml .
COPY control/pom.xml control/
COPY app/pom.xml app/

# 4. Descarga las dependencias (si cambian los POMs)
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline

# 5. Copia el código fuente
COPY control/src control/src
COPY app/src app/src

# 6. Compila y empaqueta la aplicación
# El Shade Plugin en app/pom.xml creará el Uber-JAR ejecutable
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean install -DskipTests


# ETAPA DE EJECUCIÓN (RUNTIME)
# Usa el JRE (solo entorno de ejecución) para un contenedor más pequeño
FROM eclipse-temurin:21-jre-jammy AS stage-1

# 1. Define el directorio de trabajo
WORKDIR /app

# 2. Copia el Uber-JAR ejecutable desde la etapa 'builder'
# El archivo final se llama 'app.jar' gracias al shade plugin.
COPY --from=builder /app/app/target/app.jar ./app.jar

# 3. Comando de ejecución
CMD ["java", "-jar", "app.jar"]