package com.ioteste.control;

public class DataSwitch {
    private int id;
    private boolean output;
    
    public DataSwitch(int id, boolean output) {
        this.id = id;
        this.output = output;
    }

    public int getId() {
        return id;
    }

    public boolean estaActivo() {
        return output;
    }
}
