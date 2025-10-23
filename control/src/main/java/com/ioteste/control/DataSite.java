package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;

public class DataSite {
    private int maxEnergy;
    private List<Room> rooms;
    
    public DataSite(String siteConfig) throws JsonProcessingException, Exception {
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jNodeSiteConfig;
        
        jNodeSiteConfig = objectMapper.readTree(siteConfig);
        
        String strMaxEnergy = jNodeSiteConfig.get("maxEnergy").asText();
        int maxEnergy;
        int primerEspacio = strMaxEnergy.indexOf(' ');
        if (primerEspacio != -1) {
            maxEnergy = Integer.parseInt(strMaxEnergy.substring(0, primerEspacio));
        } else {
            maxEnergy = Integer.parseInt(strMaxEnergy);
        }
        this.maxEnergy = maxEnergy;
        
        JsonNode roomsJson = jNodeSiteConfig.get("rooms");
        List<Room> listaRoom = new ArrayList<>();
            
        if (roomsJson.isArray()) {
            ArrayNode arrayNode = (ArrayNode) roomsJson;

            for (JsonNode nodoRoom : arrayNode) {
                String name = nodoRoom.get("name").asText();
                float expectedTemp = nodoRoom.get("expectedTemp").floatValue();
                
                String strEnergy = nodoRoom.get("energy").asText();
                int energy;
                int primerEspacio2 = strEnergy.indexOf(' ');
                if (primerEspacio2 != -1) {
                    energy = Integer.parseInt(strEnergy.substring(0, primerEspacio2));
                } else {
                    energy = Integer.parseInt(strEnergy);
                }
                
                int switchId;
                String switchy = nodoRoom.get("switch").asText();
                int ultimoIndex = switchy.lastIndexOf('/');
                if (ultimoIndex != -1) {
                    switchId = Integer.parseInt(switchy.substring(ultimoIndex + 1));
                } else {
                    throw new Exception("Error al parsear switchID, formato no esperado");
                }
                
                String srcSensor = nodoRoom.get("srcSensor").asText();
                
                listaRoom.add(new Room(name, expectedTemp, energy, switchId, srcSensor));
            }
        }
        this.rooms = listaRoom;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public List<Room> getRooms() {
        return rooms;
    }
}
