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

    // VARIABLE PARA PERSISTIR EL ESTADO CONOCIDO DE LOS SWITCHES
    private List<DataSwitch> knownSwitchStatus = new ArrayList<>();

    public static void main(String[] args) {
        App myApp = new App();
        myApp.start();
    }

    // Método simulado que siempre retorna "off" (o falla)
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

        // INICIALIZACIÓN DEL ESTADO PERSISTENTE
        this.knownSwitchStatus = getInitialSwitchesStatus();

        String brokerUrl = "tcp://ioteste-broker:1883";
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
                    public void messageArrived(String topic, MqttMessage message) throws Exception { // **CORRECCIÓN CLAVE**
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

            // USAR EL ESTADO PERSISTENTE
            Context context = new Context(LocalDateTime.now());
            AppData appData = new AppData(siteConfig, sensorData, this.knownSwitchStatus, context);

            ControlResponse response = controller.powerManagement(appData);

            executeOperations(response.getOperations());

            // ACTUALIZAR EL ESTADO PERSISTENTE DESPUÉS DE LA EJECUCIÓN DEL CONTROLADOR
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

    // MÉTODO MODIFICADO: Solo se usa para obtener el estado INICIAL
    private List<DataSwitch> getInitialSwitchesStatus() {
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
            
            
            for (DataSwitch ds : this.knownSwitchStatus) {
                if (ds.getSwitchURL().equals(op.getSwitchURL())) {
                    ds.setActive(op.getPower()); 
                    break;
                }
            }

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