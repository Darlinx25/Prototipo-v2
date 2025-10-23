package com.ioteste.control;

public class Room {
    private String name; //name = sensor src
    private float expectedTemp;
    private float energy;
    private String switchURL;

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
}
