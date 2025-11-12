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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private String energyContract = EnergyCost.TEST_CONTRACT_30S;

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private HttpClient client = HttpClient.newHttpClient();
    private MqttClient mqttClient;

    protected Controller controller = new DefaultController();
    private DataSite siteConfig;

    private List<DataSwitch> knownSwitchStatus = new ArrayList<>();
    
    private Map<String, Long> sensorHeartbeats = new ConcurrentHashMap<>();
    
    private static final long SENSOR_TIMEOUT_MS = 5000;

    private final Object switchLock = new Object();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("switchCommandExecutor");
        t.setDaemon(true);
        return t;
    });

    public static void main(String[] args) {
        App myApp = new App();
        myApp.start();
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    private String getSwitchStatus(String switchURL) throws IOException, InterruptedException {
        int maxRetries = 5;
        int retryCount = 0;
        long waitTime = 5000;
        while (true) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(switchURL))
                        .build();
                HttpResponse<String> response = this.client.send(request, BodyHandlers.ofString());
                return response.body();
            } catch (IOException | InterruptedException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    logger.error("FATAL: No se pudo obtener estado del switch {} tras {} intentos. Causa: {}", switchURL, maxRetries, e.getMessage());
                    throw e;
                }
                logger.warn("Falla al obtener estado de switch {}. Reintentando ({}/{}) en {}ms", switchURL, retryCount, maxRetries, waitTime);
                Thread.sleep(waitTime);
            }
        }
    }

    private String postSwitchOp(String switchURL, String jsonBody) throws IOException, InterruptedException {
        int maxRetries = 5;
        int retryCount = 0;
        long waitTime = 5000;
        while (true) {
            try {
                BodyPublisher bodyPublisher = BodyPublishers.ofString(jsonBody);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(switchURL))
                        .header("Content-Type", "application/json")
                        .POST(bodyPublisher)
                        .build();
                HttpResponse<String> response = this.client.send(request, BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                    logger.info("ACK recibido de {}: {}", switchURL, response.body());
                } else {
                    logger.warn("No se recibió ACK válido del switch {} (HTTP {})", switchURL, response.statusCode());
                }

                return response.body();
            } catch (IOException | InterruptedException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    logger.error("FATAL: No se pudo enviar comando a switch {} tras {} intentos. Causa: {}", switchURL, maxRetries, e.getMessage());
                    throw e;
                }
                logger.warn("Error al enviar comando a switch {}. Reintentando ({}/{}) en {}ms", switchURL, retryCount, maxRetries, waitTime);
                Thread.sleep(waitTime);
            }
        }
    }


    private void start() {
        int maxRetriesSite = 10;
        int retryCountSite = 0;
        long waitTimeSite = 5000;
        while (retryCountSite < maxRetriesSite) {
            try {
                logger.info("Intentando cargar configuración del sitio... (Intento: {})", (retryCountSite + 1));
                this.siteConfig = loadSiteConfig();
                logger.info("Configuración del sitio cargada exitosamente.");
                break;
            } catch (Exception e) {
                retryCountSite++;
                String errorMsg = e.getMessage();
                if (errorMsg == null) {
                    errorMsg = "Destination unreachable.";
                }
                logger.error("Error al cargar la configuración del sitio. Reintentando en {}ms. Causa: {}", waitTimeSite, errorMsg);
                try {
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

        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    logger.info("Conexión MQTT {} con broker {}", (reconnect ? "reestablecida" : "exitosa"), serverURI);
                    try {
                        if (siteConfig != null && siteConfig.getRooms() != null) {
                            Set<String> uniqueBaseTopics = new HashSet<>();
                            for (Room room : siteConfig.getRooms()) {
                                String sensorTopic = room.getSensor();
                                if (sensorTopic == null || sensorTopic.isBlank()) {
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
                        } else {
                            logger.warn("No hay configuración de siteConfig al reconectar MQTT.");
                        }
                    } catch (MqttException e) {
                        logger.error("Error al re-suscribirse tras reconexión MQTT", e);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    logger.error("Conexión MQTT perdida, intentando reconectar...");

                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    handleSensorMessage(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            logger.info("Intentando conectar a broker MQTT: {}", brokerUrl);
            mqttClient.connect(options);
            logger.info("¡Conexión MQTT exitosa!");
            logger.info("IoTEste App iniciado. Escuchando tópicos de sensores.");

            startPeakHourMonitor();
            
            startSensorWatchdog();

            Thread.currentThread().join();

            commandExecutor.shutdown();
            try {
                if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    commandExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                commandExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

        } catch (MqttException e) {
            logger.error("Error fatal de conexión MQTT. Causa: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("Aplicación interrumpida.");
            Thread.currentThread().interrupt();
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
            sensorHeartbeats.put(roomName, System.currentTimeMillis());
            sensorData.setRoom(roomName);
            Context context = new Context(LocalDateTime.now());
            List<DataSwitch> switchSnapshot = snapshotSwitchStatus();
            AppData appData = new AppData(siteConfig, sensorData, switchSnapshot, context);

            logger.info("--- INICIO DE PROCESAMIENTO ---");
            logger.info("Hora actual: {}", context.getCurrentTime());
            logger.info("-------------------------------");

            ControlResponse response = controller.powerManagement(appData);
            executeOperations(response.getOperations());

            if (!response.getOperations().isEmpty()) {
                synchronized (switchLock) {
                    for (Operation op : response.getOperations()) {
                        for (DataSwitch ds : this.knownSwitchStatus) {
                            if (ds.getSwitchURL().equals(op.getSwitchURL())) {
                                ds.setActive(op.getPower());
                                break;
                            }
                        }
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
        if (operations == null || operations.isEmpty()) {
            logger.info("No operations");
            return;
        }
        for (Operation op : operations) {
            final String switchURL = op.getSwitchURL();
            final String jsonCommand = createSwitchCommand(op.getPower());

            Future<Void> future = commandExecutor.submit(() -> {
                try {
                    logger.info("Comando enviado: {} -> {}", switchURL, jsonCommand);
                    String response = postSwitchOp(switchURL, jsonCommand);
                } catch (Exception e) {
                    logger.error("Falla REST al enviar comando a switch {}.", switchURL, e);
                }
                return null;
            });

            try {
                future.get();
            } catch (ExecutionException ee) {
                logger.error("Error executing switch command for {}: {}", switchURL, ee.getCause().getMessage());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while sending command to {}", switchURL);
                break;
            }
        }
    }

    private String createSwitchCommand(boolean power) {
        return String.format("{\"state\": %b}", power);
    }

    private String readJsonFileAsString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private void startPeakHourMonitor() {

        Thread peakMonitorThread = new Thread(() -> {
            logger.info("Starting peak-hour monitor thread using contract: {}", energyContract);
            boolean lastWasPeak = false;

            while (true) {
                try {
                    EnergyCost.EnergyZone zone = EnergyCost.currentEnergyZone(energyContract);
                    boolean isPeak = (zone.current() == EnergyCost.HIGH);

                    if (isPeak && !lastWasPeak) {
                        logger.info("Entering peak hours — turning off all switches.");
                        turnOffAllSwitches();
                    } else if (!isPeak && lastWasPeak) {
                        logger.info("Leaving peak hours — normal operation resumed.");
                    }

                    lastWasPeak = isPeak;

                    long delay = zone.nextTS() - System.currentTimeMillis() + 1000;
                    if (delay < 0) {
                        delay = 5000;
                    }
                    Thread.sleep(delay);

                } catch (InterruptedException e) {
                    logger.warn("Peak-hour monitor interrupted.");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in peak-hour monitor thread", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        peakMonitorThread.setName("peakMonitorThread");
        peakMonitorThread.setDaemon(true);
        peakMonitorThread.start();
    }

    private void turnOffAllSwitches() {
        List<Operation> operations = new ArrayList<>();

        synchronized (switchLock) {
            for (DataSwitch s : this.knownSwitchStatus) {
                if (s.isActive()) {
                    operations.add(new Operation(s.getSwitchURL(), false));
                }
            }
        }

        if (!operations.isEmpty()) {
            executeOperations(operations);

            synchronized (switchLock) {
                for (Operation op : operations) {
                    for (DataSwitch ds : this.knownSwitchStatus) {
                        if (ds.getSwitchURL().equals(op.getSwitchURL())) {
                            ds.setActive(false);
                        }
                    }
                }
            }
        }

        logger.info("All active switches turned off due to peak hours.");
    }

    private List<DataSwitch> snapshotSwitchStatus() {
        synchronized (switchLock) {
            List<DataSwitch> copy = new ArrayList<>(this.knownSwitchStatus.size());
            for (DataSwitch ds : this.knownSwitchStatus) {
                copy.add(new DataSwitch(ds.getSwitchURL(), ds.isActive()));
            }
            return copy;
        }
    }

    private void startSensorWatchdog() {
        Thread watchdogThread = new Thread(() -> {
            logger.info("Iniciando Watchdog de sensores (Timeout: {}ms)", SENSOR_TIMEOUT_MS);
            
            while (true) {
                try {
                    long now = System.currentTimeMillis();
                    List<Operation> timeoutOperations = new ArrayList<>();

                    if (siteConfig != null) {
                        for (Room room : siteConfig.getRooms()) {
                            String rName = room.getName();
                            Long lastHeartbeat = sensorHeartbeats.get(rName);

                            if (lastHeartbeat != null && (now - lastHeartbeat) > SENSOR_TIMEOUT_MS) {
                              
                                boolean isSwitchOn = false;
                                synchronized (switchLock) {
                                    for (DataSwitch ds : knownSwitchStatus) {
                                        if (ds.getSwitchURL().equals(room.getSwitchURL())) {
                                            isSwitchOn = ds.isActive();
                                            break;
                                        }
                                    }
                                }

                                if (isSwitchOn) {
                                    logger.warn("ALERTA: Sensor en '{}' no responde hace {}ms. Apagando switch por seguridad.", 
                                                rName, (now - lastHeartbeat));
                                    timeoutOperations.add(new Operation(room.getSwitchURL(), false));
                                }
                            }
                        }
                    }

                    
                    if (!timeoutOperations.isEmpty()) {
                        executeOperations(timeoutOperations);
                        
                        
                        synchronized (switchLock) {
                            for (Operation op : timeoutOperations) {
                                for (DataSwitch ds : knownSwitchStatus) {
                                    if (ds.getSwitchURL().equals(op.getSwitchURL())) {
                                        ds.setActive(false);
                                    }
                                }
                            }
                        }
                    }

                    
                    Thread.sleep(5000);

                } catch (InterruptedException e) {
                    logger.warn("Watchdog interrumpido");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error en hilo Watchdog", e);
                }
            }
        });
        
        watchdogThread.setName("SensorWatchdog");
        watchdogThread.setDaemon(true); 
        watchdogThread.start();
    }
}

