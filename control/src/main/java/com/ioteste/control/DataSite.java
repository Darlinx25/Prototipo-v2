package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;

public class DataSite {
    private float maxEnergy;
    private List<Room> rooms;
    
    public DataSite(String siteConfig) throws JsonProcessingException {
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jNodeSiteConfig;
        
        jNodeSiteConfig = objectMapper.readTree(siteConfig);
        
        // 1. Parsear MaxEnergy ("4 kWh" -> 4.0f)
        String strMaxEnergy = jNodeSiteConfig.get("maxEnergy").asText();
        float maxEnergy;
        int firstSpace = strMaxEnergy.indexOf(' ');
        if (firstSpace != -1) {
            maxEnergy = Float.parseFloat(strMaxEnergy.substring(0, firstSpace));
        } else {
            maxEnergy = Float.parseFloat(strMaxEnergy);
        }
        this.maxEnergy = maxEnergy;
        
        // 2. Parsear la lista de Rooms
        JsonNode roomsJson = jNodeSiteConfig.get("rooms");
        List<Room> roomList = new ArrayList<>();
            
        if (roomsJson.isArray()) {
            ArrayNode arrayNode = (ArrayNode) roomsJson;

            for (JsonNode nodoRoom : arrayNode) {
                String name = nodoRoom.get("name").asText();
                String expectedTempStr = nodoRoom.get("expectedTemp").asText();
                float expectedTemp = Float.parseFloat(expectedTempStr);
                
                String strEnergy = nodoRoom.get("energy").asText();
                float energy;
                int firstSpace2 = strEnergy.indexOf(' ');
                if (firstSpace2 != -1) {
                    energy = Float.parseFloat(strEnergy.substring(0, firstSpace2));
                } else {
                    energy = Float.parseFloat(strEnergy);
                }
                
                String switchURL = nodoRoom.get("switch").asText();
                
                roomList.add(new Room(name, expectedTemp, energy, switchURL));
            }
        }
        this.rooms = roomList;
    }

    public float getMaxEnergy() {
        return maxEnergy;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public void setMaxEnergy(float maxEnergy) {
        this.maxEnergy = maxEnergy;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }
}