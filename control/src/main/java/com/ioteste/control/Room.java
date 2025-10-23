package com.ioteste.control;

public class Room {
    private String name;
    private float expectedTemp;
    private int energy;
    private int switchId;
    private String srcSensor;

    public Room(String name, float expectedTemp, int energy, int switchId, String srcSensor) {
        this.name = name;
        this.expectedTemp = expectedTemp;
        this.energy = energy;
        this.switchId = switchId;
        this.srcSensor = srcSensor;
    }

    public String getName() {
        return name;
    }

    public float getExpectedTemp() {
        return expectedTemp;
    }

    public int getEnergy() {
        return energy;
    }

    public int getSwitchId() {
        return switchId;
    }

    public String getSrcSensor() {
        return srcSensor;
    }
}
