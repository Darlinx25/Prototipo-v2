package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DataSensor {
    private String src; //src = room name
    private LocalDateTime dateTime;
    private float temperature;
    
    public DataSensor(String sensorData) throws JsonProcessingException {
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jNodeSensorData;
        
        jNodeSensorData = objectMapper.readTree(sensorData);
        
        String src = jNodeSensorData.get("src").asText();
        this.src = src;
        
        long timeMillisec = (long) (jNodeSensorData.get("params").get("ts").floatValue() * 1000);
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillisec), ZoneId.systemDefault());
        this.dateTime = dateTime;
        
        float temperature = jNodeSensorData.get("params").get("temperature:0").get("tC").floatValue();
        this.temperature = temperature;
    }

    public String getSrc() {
        return src;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
    
    
}
