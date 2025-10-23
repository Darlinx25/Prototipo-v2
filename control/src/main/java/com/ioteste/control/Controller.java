package com.ioteste.control;

import java.util.List;

/*
A esta interface siempre
le tienen que llegar
JSON bien formados, es decir
con los campos adecuados
no null y retornará las
operaciones sugeridas
a realizar
*/
public interface Controller {

    /**
     *
     * @param siteConfig
     * @param sensorData
     * @param switchStatus
     * @return un string JSON con las operaciones
     * a realizar para cada switch
     */
    public List<Operacion> controlTemperatura(String siteConfig, String sensorData, List<DataSwitch> switchStatus);
}
