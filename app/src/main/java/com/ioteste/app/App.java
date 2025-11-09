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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class App {
    private HttpClient client = HttpClient.newHttpClient();
    private MqttClient mqttClient;
    
    private Controller controller = new DefaultController();
    private DataSite siteConfig; 

    public static void main(String[] args) {
        App myApp = new App();
        myApp.start();
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
    
    private void start(){
        try {
            this.siteConfig = loadSiteConfig();
        } catch (Exception e) {
            System.err.println("FATAL: No se pudo cargar la configuración del sitio.");
            e.printStackTrace();
            return; 
        }

        String brokerUrl = "tcp://mosquitto:1883";
        String topic = "habitacion/ambiente"; 
        String clientId = "app-" + UUID.randomUUID().toString();

        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            mqttClient.setCallback(new MqttCallback() {
                
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("Conexión perdida: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    handleSensorMessage(payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            mqttClient.connect(options);
            mqttClient.subscribe(topic);
            
            System.out.println("IoTEste App iniciado. Escuchando en tópico: " + topic);
            
            while (true) {
                Thread.sleep(1000);
            }
        } catch (MqttException | InterruptedException e) {
            System.err.println("Error en el cliente MQTT: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private DataSite loadSiteConfig() throws Exception {
        String siteConfigJson = """
                                {
                                    "site": "oficina001",
                                    "maxEnergy": "4 kWh", 
                                    "timeSlot": {
                                      "contractType":"std",
                                      "refreshPeriod":"10000 ms"
                                      },
                                    "rooms": [
                                        {
                                            "name": "office1",
                                            "expectedTemp": "22",
                                            "energy": "2 kWh",
                                            "switch": "http://host:port/switch/1",
                                            "sensor": "mqtt:topic1"
                                        },
                                        {
                                            "name": "shellyhtg3-84fce63ad204",
                                            "expectedTemp": "21",
                                            "energy": "2 kWh",
                                            "switch": "http://host:port/switch/2",
                                            "sensor": "mqtt:topic2"
                                        }
                                    ]
                                }""";
        return new DataSite(siteConfigJson);
    }
    
    private void handleSensorMessage(String payload) {
        try {
            DataSensor sensorData = new DataSensor(payload);
            
            List<DataSwitch> switchStatus = getSwitchesStatus();
            
            Context context = new Context(LocalDateTime.now());
            AppData appData = new AppData(siteConfig, sensorData, switchStatus, context);
            
            ControlResponse response = controller.powerManagement(appData);
            
            executeOperations(response.getOperations());

        } catch (Exception e) {
            System.err.println("Error procesando mensaje o ejecutando control.");
            e.printStackTrace();
        }
    }

    private List<DataSwitch> getSwitchesStatus() {
        List<DataSwitch> switches = new ArrayList<>();
        if (siteConfig == null) return switches;

        for (Room room : siteConfig.getRooms()) {
            String switchURL = room.getSwitchURL();
            try {
                String jsonResponse = getSwitchStatus(switchURL); 
                
                DataSwitch status = parseSwitchStatus(switchURL, jsonResponse);
                switches.add(status);
                
            } catch (Exception e) {
                System.err.printf("Falla REST al obtener estado de switch %s. Se asume apagado.\n", switchURL);
                switches.add(new DataSwitch(switchURL, false)); 
            }
        }
        return switches;
    }

    private DataSwitch parseSwitchStatus(String switchURL, String jsonResponse) {
        boolean isActive = jsonResponse.contains("\"state\": \"on\""); 
        return new DataSwitch(switchURL, isActive);
    }

    private void executeOperations(List<Operation> operations) {
        for (Operation op : operations) {
            String jsonCommand = createSwitchCommand(op.getPower());
            try {
                String response = postSwitchOp(op.getSwitchURL(), jsonCommand);
                System.out.printf("Comando OK: %s -> Power: %s\n", op.getSwitchURL(), op.getPower());
            } catch (Exception e) {
                System.err.printf("Falla REST al enviar comando a switch %s.\n", op.getSwitchURL());
            }
        }
    }

    private String createSwitchCommand(boolean power) {
        String state = power ? "on" : "off";
        return String.format("{\"cmd\": \"set\", \"state\": \"%s\"}", state);
    }
}