package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DataSensor {
    private String srcSensor;
    private LocalDateTime fechaHora;
    private float temperatura;
    
    public DataSensor(String sensorData) throws JsonProcessingException {
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jNodeSensorData;
        
        jNodeSensorData = objectMapper.readTree(sensorData);
        
        String srcSensor = jNodeSensorData.get("src").asText();
        this.srcSensor = srcSensor;
        
        long tiempoMiliseg = (long) (jNodeSensorData.get("params").get("ts").floatValue() * 1000);
        LocalDateTime fechaHora = LocalDateTime.ofInstant(Instant.ofEpochMilli(tiempoMiliseg), ZoneId.systemDefault());
        this.fechaHora = fechaHora;
        
        float temperatura = jNodeSensorData.get("params").get("temperature:0").get("tC").floatValue();
        this.temperatura = temperatura;
    }

    public String getSrcSensor() {
        return srcSensor;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public float getTemperatura() {
        return temperatura;
    }
}
