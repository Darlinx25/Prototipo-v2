package com.ioteste.control;

public class Operation {
    private String switchURL;
    private boolean power;

    public Operation(String switchURL, boolean power) {
        this.switchURL = switchURL;
        this.power = power;
    }

    public String getSwitchURL() {
        return switchURL;
    }

    public boolean isPower() {
        return power;
    }
    
    
}
