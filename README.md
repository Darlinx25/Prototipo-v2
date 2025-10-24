# Prototipo IoTEste - Control de Temperatura

## UTEC - Ingeniería de Software Grupo 2

---

## Introducción
Prototipo de un componente para controlar la calefacción eléctrica de varias habitaciones.  
Cada habitación tiene un sensor de temperatura y un switch que permite encender o apagar la calefacción.  
El sistema utiliza un JSON de configuración que indica la temperatura deseada por habitación y el consumo eléctrico permitido.  
El componente ajusta los switches para mantener la temperatura óptima.

---
```
## Estructura del proyecto
Prototipo-v2/
├─ control/ # API de control, lógica y pruebas unitarias
│ ├─ src/
│ └─ pom.xml
├─ app/ # Aplicación principal y posibles integraciones
│ ├─ src/
│ └─ pom.xml
└─ pom.xml
```

## Funcionalidad
- Lectura de JSON de configuración de habitaciones.  
- Recepción de datos de sensores (MQTT) y cálculo de acciones sobre switches.  
- Encendido/apagado de calefacción mediante REST hacia los switches.  
- Pruebas unitarias que simulan escenarios de encendido/apagado según temperatura.

---
