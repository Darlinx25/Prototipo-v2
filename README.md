# Prototipo IoTEste - Control de Temperatura

## UTEC - Ingeniería de Software Grupo 2

---

## Introducción
Prototipo de un componente para controlar la calefacción eléctrica de varias habitaciones.  
Cada habitación tiene un sensor de temperatura y un switch que permite encender o apagar la calefacción.  
El sistema utiliza un JSON de configuración que indica la temperatura deseada por habitación y el consumo eléctrico permitido.  
El componente ajusta los switches para mantener la temperatura óptima.

---

## Estructura del proyecto

```
Prototipo-v2/
├─ .github/
├─ app/
│  ├─ src/
│  ├─ target/
│  ├─ pom.xml
│  └─ dependency-reduced-pom.xml
├─ control/
│  ├─ src/
│  ├─ target/
│  └─ pom.xml
├─ .gitignore
├─ docker-compose.yaml
├─ Dockerfile
├─ mosquitto.conf
├─ pom.xml
└─ README.md

```

## Funcionalidad
- Lectura de JSON de configuración de habitaciones.  
- Recepción de datos de sensores (**MQTT**) y cálculo de acciones sobre switches.  
- Encendido/apagado de calefacción mediante **REST** hacia los switches.  
- Pruebas unitarias que simulan escenarios de encendido/apagado según temperatura.

---

## 🚀 Ejecución y Consumo del Prototipo (Docker) 🐳

El proyecto utiliza **Docker Compose** para orquestar la aplicación principal (`ioteste-app`), el broker MQTT y otros servicios necesarios.

### 1. Iniciar los Servicios

Para levantar todos los contenedores (aplicación, broker MQTT, etc.), usa el siguiente comando.

| Situación | Comando | Descripción |
| :--- | :--- | :--- |
| **Primer inicio** o **sin cambios en el código** | `docker compose up -d` | Inicia los servicios en segundo plano. |
| **Hay cambios en `app.java`** o **siempre que se modifiquen dependencias** | `docker compose up --build -d` | Reconstruye la imagen del contenedor `app` antes de iniciar los servicios. |

### 2. Monitorear la Aplicación (Consumir Mensajes de Control)

Para ver el log de la aplicación principal (`ioteste-app`), que muestra los mensajes recibidos del sensor, la hora, la tarifa actual y las acciones tomadas sobre los *switches* (calefacción):

```powershell
docker logs -f ioteste-app
```

### 3. Enviar Datos de Sensores (Publicar Mensajes MQTT)
Utiliza mosquitto_pub en un contenedor temporal para simular que un sensor de temperatura envía datos al broker MQTT.

🔸 Ejemplo de envío (Windows/PowerShell):
(Nota: Se usan comillas dobles " y escape \" para el JSON)

```powershell
docker run --rm --network prototipo-v2_default eclipse-mosquitto:2.0 mosquitto_pub -h ioteste-broker -t habitacion/ambiente -m "{\"room\":\"office1\", \"temperature\":19, \"humidity\":60}"
```

🔹 Ejemplo de envío (Linux/WSL/macOS):
(Nota: Se usan comillas simples ' para el JSON)
```
docker run --rm --network prototipo-v2_default eclipse-mosquitto:2.0 mosquitto_pub -h ioteste-broker -t habitacion/ambiente -m '{"room":"office1", "temperature":19.0, "humidity":60}'
```
