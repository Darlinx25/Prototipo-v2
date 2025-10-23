package com.ioteste.control;

public class DataSwitch {
    private String switchURL;
    private boolean active;
    
    public DataSwitch(String switchURL, boolean active) {
        this.switchURL = switchURL;
        this.active = active;
    }

    public String getSwitchURL() {
        return switchURL;
    }

    public boolean isActive() {
        return active;
    }
}
