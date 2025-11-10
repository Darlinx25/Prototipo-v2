// appdata.java
package com.ioteste.control;

import java.util.List;

public class AppData {
    private DataSite siteConfig;
    private DataSensor sensorData;
    private List<DataSwitch> switchStatus;
    private Context context;
    
    public AppData(DataSite siteConfig, DataSensor sensorData, List<DataSwitch> switchStatus, Context context) {
        this.siteConfig = siteConfig;
        this.sensorData = sensorData;
        this.switchStatus = switchStatus;
        this.context = context;
    }

    public DataSite getSiteConfig() { return siteConfig; }
    public DataSensor getSensorData() { return sensorData; }
    public List<DataSwitch> getSwitchStatus() { return switchStatus; }
    public Context getContext() { return context; }

    public void setSiteConfig(DataSite siteConfig) { this.siteConfig = siteConfig; }
    public void setSensorData(DataSensor sensorData) { this.sensorData = sensorData; }
    public void setSwitchStatus(List<DataSwitch> switchStatus) { this.switchStatus = switchStatus; }
    public void setContext(Context context) { this.context = context; }
}