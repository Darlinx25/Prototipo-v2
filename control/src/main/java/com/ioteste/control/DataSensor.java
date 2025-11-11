package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class DataSensor {

    private String room;
    private float temperature;
    private float humidity;
    private LocalDateTime dateTime;

    public DataSensor(String sensorJson) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(sensorJson);

        this.room = root.path("src").asText("unknown-room"); 

        JsonNode params = root.path("params");
        this.temperature = (float) params.path("temperature:0").path("tC").asDouble(0.0);

        this.humidity = (float) params.path("humidity:0").path("rh").asDouble(0.0);

        long epochSeconds = root.path("ts").asLong(0L);
        if (epochSeconds == 0L) {
             epochSeconds = params.path("ts").asLong(Instant.now().getEpochSecond());
        }
        
        if (epochSeconds != 0L) {
            this.dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds), 
                ZoneOffset.UTC
            );
        } else {
            this.dateTime = LocalDateTime.now(ZoneOffset.UTC); 
        }
    }

    public String getRoom() {
        return room;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getHumidity() {
        return humidity;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public void setHumidity(float humidity) {
        this.humidity = humidity;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
}