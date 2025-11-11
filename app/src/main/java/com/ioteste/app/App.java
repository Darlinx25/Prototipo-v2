package com.ioteste.app;

import com.ioteste.control.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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

public class App {

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
        try {
            this.siteConfig = loadSiteConfig();
        } catch (Exception e) {
            System.err.println("FATAL: No se pudo cargar la configuración del sitio.");
            e.printStackTrace();
            return;
        }

        this.knownSwitchStatus = getInitialSwitchesStatus();

        String brokerUrl = "tcp://localhost:1883";
        System.out.println("Modo de Integración. Usando broker de cajaNegra en: " + brokerUrl);


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

                System.out.println("Intentando conectar a broker: " + brokerUrl + " (Intento: " + (retryCount + 1) + ")");

                mqttClient.setCallback(new MqttCallback() {

                    @Override
                    public void connectionLost(Throwable cause) {
                        System.err.println("Conexión perdida: " + cause.getMessage());
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        String payload = new String(message.getPayload());
                        System.out.printf("Mensaje recibido en tópico [%s]\n", topic);
                        // --- CAMBIO CLAVE: Pasa el TÓPICO al handler ---
                        handleSensorMessage(topic, payload);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });

                mqttClient.connect(options);
                System.out.println("¡Conexión MQTT exitosa!");
                System.out.println("Suscribiendo a tópicos de sensores desde siteConfig...");

                if (siteConfig == null || siteConfig.getRooms() == null) {
                    System.err.println("FATAL: No hay configuración de habitaciones para suscribir.");
                    return; 
                }

                Set<String> uniqueBaseTopics = new HashSet<>();
                for (Room room : siteConfig.getRooms()) {
                    String sensorTopic = room.getSensor(); 

                    if (sensorTopic == null || sensorTopic.isBlank()) {
                        System.err.println("Advertencia: Habitación '" + room.getName() + "' no tiene tópico de sensor definido.");
                        continue;
                    }

                    if (sensorTopic.startsWith("mqtt:")) {
                        sensorTopic = sensorTopic.substring(5);
                    }
                    uniqueBaseTopics.add(sensorTopic); 
                }
                
                for (String baseTopic : uniqueBaseTopics) {
                    String wildcardTopic = baseTopic + "/+"; 
                    
                    System.out.println("Suscribiendo a: " + wildcardTopic);
                    
                    mqttClient.subscribe(wildcardTopic, (topic, message) -> {
                        String payload = new String(message.getPayload());
                        handleSensorMessage(topic, payload); 
                    });
                }
                
                System.out.println("IoTEste App iniciado. Escuchando tópicos de sensores.");

                while (true) {
                    Thread.sleep(1000);
                }
            } catch (MqttException e) {
                retryCount++;
                System.err.println("Error de conexión MQTT. Reintentando en " + waitTime + "ms. Causa: " + e.getMessage());
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                System.err.println("Aplicación interrumpida.");
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (retryCount >= maxRetries) {
            System.err.println("FATAL: Se agotaron los reintentos de conexión MQTT. Saliendo de la aplicación.");
        }
    }
    
    public DataSite loadSiteConfig() throws Exception {
        String configURL = "http://localhost:8080/site-config";
        System.out.println("Cargando configuración del sitio desde: " + configURL);

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
                System.err.println("Error: Mensaje de tópico '" + topic + "' no se pudo mapear a una habitación.");
                return; 
            }
            
            sensorData.setRoom(roomName); 

            Context context = new Context(LocalDateTime.now());
            AppData appData = new AppData(siteConfig, sensorData, this.knownSwitchStatus, context);

            System.out.println("--- INICIO DE PROCESAMIENTO ---");
            System.out.printf("Hora actual: %s\n", context.getCurrentTime());
            System.out.println("-------------------------------");

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
            System.err.println("Error procesando mensaje o ejecutando control.");
            e.printStackTrace();
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
                System.err.printf("Falla REST al obtener estado inicial de switch %s. Se asume apagado.\n", switchURL);
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
                System.out.printf("Comando OK: %s -> %s\n", op.getSwitchURL(), jsonCommand);
            } catch (Exception e) {
                System.err.printf("Falla REST al enviar comando a switch %s.\n", op.getSwitchURL());
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