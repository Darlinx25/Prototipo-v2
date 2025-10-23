package com.ioteste.control;

public class Operacion {
    private int switchId;
    private boolean power;

    public Operacion(int switchId, boolean power) {
        this.switchId = switchId;
        this.power = power;
    }

    public int getSwitchId() {
        return switchId;
    }

    public boolean isPower() {
        return power;
    }
    
    
}
