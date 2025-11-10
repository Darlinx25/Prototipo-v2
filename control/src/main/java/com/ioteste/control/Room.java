package com.ioteste.control;

public class Room {
    private String name;
    private float expectedTemp;
    private float energy;
    private String switchURL;

    public Room() {}
    
    public Room(String name, float expectedTemp, float energy, String switchURL) {
        this.name = name;
        this.expectedTemp = expectedTemp;
        this.energy = energy;
        this.switchURL = switchURL;
    }

    public String getName() {
        return name;
    }

    public float getExpectedTemp() {
        return expectedTemp;
    }

    public float getEnergy() {
        return energy;
    }

    public String getSwitchURL() {
        return switchURL;
    }
    
    public void setName(String name) { this.name = name; }
    public void setExpectedTemp(float expectedTemp) { this.expectedTemp = expectedTemp; }
    public void setEnergy(float energy) { this.energy = energy; }
    public void setSwitchURL(String switchURL) { this.switchURL = switchURL; }
}