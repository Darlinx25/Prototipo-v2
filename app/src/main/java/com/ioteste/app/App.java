package com.ioteste.app;

import com.ioteste.control.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private HttpClient client = HttpClient.newHttpClient();
    private MqttClient mqttClient;

    protected Controller controller = new DefaultController();    
    private DataSite siteConfig;

    private List<DataSwitch> knownSwitchStatus = new ArrayList<>();

    public static void main(String[] args) {
        App myApp = new App();
        myApp.start();
    }

    
    public void setController(Controller controller) {
        this.controller = controller;
    }


    
    private String getSwitchStatus(String switchURL) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(switchURL)).build();
        HttpResponse<String> response = this.client.send(request, BodyHandlers.ofString());

        return response.body();
    }
    
    

    private String postSwitchOp(String switchURL, String jsonBody) throws IOException, InterruptedException {
        BodyPublisher bodyPublisher = BodyPublishers.ofString(jsonBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(switchURL))
                .header("Content-Type", "application/json")
                .POST(bodyPublisher)
                .build();
        HttpResponse<String> response = this.client.send(request, BodyHandlers.ofString());

        return response.body();
    }

    private void start() {
        
        int maxRetriesSite = 10;
        int retryCountSite = 0;
        long waitTimeSite = 3000;
        
        while (retryCountSite < maxRetriesSite) {
            try {
                logger.info("Intentando cargar configuración del sitio... (Intento: {})", (retryCountSite + 1));
                this.siteConfig = loadSiteConfig();
                logger.info("Configuración del sitio cargada exitosamente.");
            } catch (Exception e) {
                retryCountSite++;
                logger.error("Error al cargar la configuración del sitio. Reintentando en {}ms. Causa: {}", waitTimeSite, e.getMessage());
                try{
                    Thread.sleep(waitTimeSite);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } 
            }
        }
        
        this.knownSwitchStatus = getInitialSwitchesStatus();
        String brokerUrl = "tcp://localhost:1883";
        logger.info("Modo de Integración. Usando broker de cajaNegra en: {}", brokerUrl);


        String clientId = "app-" + UUID.randomUUID().toString();
        int maxRetries = 10;
        int retryCount = 0;
        long waitTime = 3000;

        while (retryCount < maxRetries) {
            try {
                mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);

                logger.info("Intentando conectar a broker: {} (Intento: {})", brokerUrl, (retryCount + 1));

                mqttClient.setCallback(new MqttCallback() {

                    @Override
                    public void connectionLost(Throwable cause) {
                        logger.error("Conexión MQTT perdida", cause);
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        String payload = new String(message.getPayload());
                        logger.info("Mensaje recibido en tópico [{}]", topic);
                        // --- CAMBIO CLAVE: Pasa el TÓPICO al handler ---
                        handleSensorMessage(topic, payload);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });

                mqttClient.connect(options);
                logger.info("¡Conexión MQTT exitosa!");
                logger.info("Suscribiendo a tópicos de sensores desde siteConfig...");

                if (siteConfig == null || siteConfig.getRooms() == null) {
                    logger.error("FATAL: No hay configuración de habitaciones para suscribir.");
                    return; 
                }

                Set<String> uniqueBaseTopics = new HashSet<>();
                for (Room room : siteConfig.getRooms()) {
                    String sensorTopic = room.getSensor(); 

                    if (sensorTopic == null || sensorTopic.isBlank()) {
                        logger.warn("Advertencia: Habitación '{}' no tiene tópico de sensor definido.", room.getName());
                        continue;
                    }

                    if (sensorTopic.startsWith("mqtt:")) {
                        sensorTopic = sensorTopic.substring(5);
                    }
                    uniqueBaseTopics.add(sensorTopic); 
                }
                
                for (String baseTopic : uniqueBaseTopics) {
                    String wildcardTopic = baseTopic + "/+"; 
                    
                    logger.info("Suscribiendo a: {}", wildcardTopic);
                    
                    mqttClient.subscribe(wildcardTopic, (topic, message) -> {
                        String payload = new String(message.getPayload());
                        handleSensorMessage(topic, payload); 
                    });
                }
                
                logger.info("IoTEste App iniciado. Escuchando tópicos de sensores.");

                while (true) {
                    Thread.sleep(1000);
                }
            } catch (MqttException e) {
                retryCount++;
                logger.error("Error de conexión MQTT. Reintentando en {}ms. Causa: {}", waitTime, e.getMessage());
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                logger.warn("Aplicación interrumpida.");
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (retryCount >= maxRetries) {
            logger.error("FATAL: Se agotaron los reintentos de conexión MQTT. Saliendo de la aplicación.");
        }
    }
    
    public DataSite loadSiteConfig() throws Exception {
        String configURL = "http://localhost:8080/site-config";
        logger.info("Cargando configuración del sitio desde: {}", configURL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(configURL))
                .GET()
                .build();
        HttpResponse<String> response = this.client.send(request, BodyHandlers.ofString());
        return new DataSite(response.body());
    }


    public void handleSensorMessage(String topic, String payload) {    
        try {
            DataSensor sensorData = new DataSensor(payload);


            String roomName = null;
            String topicId = topic.substring(topic.lastIndexOf('/') + 1);

            for (Room room : siteConfig.getRooms()) {
                String switchURL = room.getSwitchURL();
                String switchId = switchURL.substring(switchURL.lastIndexOf('/') + 1);
                
                if (topicId.equals(switchId)) {
                     roomName = room.getName();
                     break;
                }
            }

            if (roomName == null) {
                logger.warn("Error: Mensaje de tópico '{}' no se pudo mapear a una habitación. Topic ID: {}", topic, topicId);
                return; 
            }
            
            sensorData.setRoom(roomName); 

            Context context = new Context(LocalDateTime.now());
            AppData appData = new AppData(siteConfig, sensorData, this.knownSwitchStatus, context);

            logger.info("--- INICIO DE PROCESAMIENTO ---");
            logger.info("Hora actual: {}", context.getCurrentTime());
            logger.info("-------------------------------");

            ControlResponse response = controller.powerManagement(appData);

            executeOperations(response.getOperations());

            for (Operation op : response.getOperations()) {
                for (DataSwitch ds : this.knownSwitchStatus) {
                    if (ds.getSwitchURL().equals(op.getSwitchURL())) {
                        ds.setActive(op.getPower());
                        break;
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error procesando mensaje o ejecutando control.", e);
        }
    }

    
    public List<DataSwitch> getInitialSwitchesStatus() {
        List<DataSwitch> switches = new ArrayList<>();
        if (siteConfig == null) {
            return switches;
        }

        for (Room room : siteConfig.getRooms()) {
            String switchURL = room.getSwitchURL();
            try {
                String jsonResponse = getSwitchStatus(switchURL);

                DataSwitch status = parseSwitchStatus(switchURL, jsonResponse);
                switches.add(status);

            } catch (Exception e) {
                logger.warn("Falla REST al obtener estado inicial de switch {}. Se asume apagado.", switchURL, e);
                switches.add(new DataSwitch(switchURL, false));
            }
        }
        return switches;
    }

    private DataSwitch parseSwitchStatus(String switchURL, String jsonResponse) {
        boolean isActive = jsonResponse.contains("\"output\": true");
        return new DataSwitch(switchURL, isActive);
    }

    private void executeOperations(List<Operation> operations) {
        for (Operation op : operations) {
            String jsonCommand = createSwitchCommand(op.getPower());
            try {
                String response = postSwitchOp(op.getSwitchURL(), jsonCommand);
                logger.info("Comando OK: {} -> {}", op.getSwitchURL(), jsonCommand);
            } catch (Exception e) {
                logger.error("Falla REST al enviar comando a switch {}.", op.getSwitchURL(), e);
            }
        }
    }

    private String createSwitchCommand(boolean power) {
        return String.format("{\"state\": %b}", power);
    }
    
    private String readJsonFileAsString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }
}