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
import java.util.List;
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
        if (switchURL.contains("http://host:port/")) {
            if (switchURL.endsWith("/switch/2") && Math.random() < 0.2) {
                throw new IOException("Simulated network failure to Switch 2");
            }

            String state;
            if (switchURL.endsWith("/switch/1")) {
                state = "off";
            } else if (switchURL.endsWith("/switch/2")) {
                state = "off";
            } else {
                state = "off";
            }
            return String.format("{\"state\": \"%s\", \"power\": 2}", state);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(switchURL)).build();
        HttpResponse<String> response = this.client.send(request, BodyHandlers.ofString());

        return response.body();
    }

    private String postSwitchOp(String switchURL, String jsonBody) throws IOException, InterruptedException {
        if (switchURL.contains("http://host:port/")) {
            System.out.println("SIMULACIÓN: Comando enviado a: " + switchURL + " Body: " + jsonBody);
            return "{\"state\":\"ok\"}";
        }

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

        String brokerUrl;
        String environmentVariable = System.getenv("RUN_ENVIRONMENT");
          
        if ("DOCKER".equalsIgnoreCase(environmentVariable)) {
            brokerUrl = "tcp://ioteste-broker:1883";
            System.out.println("Modo EXTERNO detectado. Usando broker: " + brokerUrl);
        } else {
            try {
                InetAddress address = InetAddress.getLocalHost();
                brokerUrl = "tcp://" + address.getHostAddress() + ":1883";
                System.out.println("Modo LOCAL (NetBeans) detectado. Usando broker: " + brokerUrl);
            } catch (UnknownHostException e) {
                brokerUrl = "tcp://localhost:1883";
                System.err.println("Advertencia: No se pudo determinar la IP local. Usando fallback: " + brokerUrl);
            }
        }

        String topic = "habitacion/ambiente";
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
                        handleSensorMessage(payload);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });

                mqttClient.connect(options);
                System.out.println("¡Conexión MQTT exitosa!");

                mqttClient.subscribe(topic);

                System.out.println("IoTEste App iniciado. Escuchando en tópico: " + topic);

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


    public void handleSensorMessage(String payload) {    
        try {
            DataSensor sensorData = new DataSensor(payload);

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
    
    /*esto es para cargar el siteconfig.json desde el filesystem luego*/
    private String readJsonFileAsString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }
}