package com.ioteste.control;

import java.util.List;

public interface Controller {

    /**
     *
     * @param siteConfig
     * @param sensorData
     * @param switchStatus
     */
    public List<Operation> powerManagement(DataSite siteConfig, DataSensor sensorData, List<DataSwitch> switchStatus);
}
