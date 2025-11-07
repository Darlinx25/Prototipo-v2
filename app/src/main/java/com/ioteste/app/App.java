package com.ioteste.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import java.util.UUID;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class App {
    private HttpClient client = HttpClient.newHttpClient();
    private MqttClient mqttClient;

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
                    
                    
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // (Nosotros no utilizamos este, pero es necesario declararlo)
                }
            });
            mqttClient.connect(options);
            mqttClient.subscribe(topic);
            while (true) {
                Thread.sleep(1000);
            }
        } catch (MqttException | InterruptedException e) {
            System.err.println("Error en el cliente MQTT: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    
    
    
    
}
