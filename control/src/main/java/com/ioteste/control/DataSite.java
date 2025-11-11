package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSite {

    private static final Logger logger = LoggerFactory.getLogger(DataSite.class);

    private float maxEnergy;
    private List<Room> rooms;
    
    
    public DataSite(String siteConfig) throws JsonProcessingException {
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jNodeSiteConfig;
        
        jNodeSiteConfig = objectMapper.readTree(siteConfig);
        
        
        String strMaxEnergy = jNodeSiteConfig.get("maxEnergy").asText();
        float maxEnergy;
        int firstSpace = strMaxEnergy.indexOf(' ');
        try {
            if (firstSpace != -1) {
                maxEnergy = Float.parseFloat(strMaxEnergy.substring(0, firstSpace));
            } else {
                maxEnergy = Float.parseFloat(strMaxEnergy);
            }
        } catch (NumberFormatException e) {
            logger.error("Error al parsear maxEnergy: '{}'. Asumiendo 0.0", strMaxEnergy, e);
            maxEnergy = 0.0f; 
        }
        this.maxEnergy = maxEnergy;
        
        
        
        
        JsonNode roomsJson = jNodeSiteConfig.get("rooms");
        List<Room> roomList = new ArrayList<>();
            
        if (roomsJson.isArray()) {
            ArrayNode arrayNode = (ArrayNode) roomsJson;

            for (JsonNode nodoRoom : arrayNode) {
                String name = nodoRoom.get("name").asText();
                
                String expectedTempStr = nodoRoom.get("expectedTemp").asText();
                float expectedTemp = 0.0f;
                try {
                    expectedTemp = Float.parseFloat(expectedTempStr);
                } catch (NumberFormatException e) {
                    logger.warn("Error al parsear expectedTemp para la habitación '{}': '{}'. Asumiendo 0.0", name, expectedTempStr);
                }
                
                String strEnergy = nodoRoom.get("energy").asText();
                float energy = 0.0f;
                int firstSpace2 = strEnergy.indexOf(' ');
                try {
                    if (firstSpace2 != -1) {
                        energy = Float.parseFloat(strEnergy.substring(0, firstSpace2));
                    } else {
                        energy = Float.parseFloat(strEnergy);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Error al parsear el consumo de energía para la habitación '{}': '{}'. Asumiendo 0.0", name, strEnergy);
                }
                
                String switchURL = nodoRoom.get("switch").asText();
                
                
                String sensor = nodoRoom.get("sensor").asText();

                roomList.add(new Room(name, expectedTemp, energy, switchURL, sensor));
            }
        }
        this.rooms = roomList;
    }
    
    

    public float getMaxEnergy() { return maxEnergy; }
    public List<Room> getRooms() { return rooms; }
    public void setMaxEnergy(float maxEnergy) { this.maxEnergy = maxEnergy; }
    public void setRooms(List<Room> rooms) { this.rooms = rooms; }
}