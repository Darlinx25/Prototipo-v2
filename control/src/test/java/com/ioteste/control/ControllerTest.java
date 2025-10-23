package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ControllerTest {
    
    public ControllerTest() {
    }

    /*prender switchs si las temperaturas son menores que las esperadas*/
    @Test
    public void testControlTemperaturaBasico() {
        String siteConfig = """
                            {
                                "site": "oficina001",
                                "maxEnergy": "14 kWh",
                                "timeSlot": {
                                  "contractType":"std",
                                  "refreshPeriod":"10000 ms"
                                  },
                                "rooms": [
                                    {
                                        "name": "office1",
                                        "expectedTemp": "22",
                                        "energy": "2 kWh",
                                        "switch": "http://host:port/switch/1",
                                        "sensor": "mqtt:topic1",
                                        "srcSensor": "random"
                                    },
                                    {
                                        "name": "office2",
                                        "expectedTemp": "21",
                                        "energy": "2 kWh",
                                        "switch": "http://host:port/switch/2",
                                        "sensor": "mqtt:topic2",
                                        "srcSensor": "shellyhtg3-84fce63ad204"
                                    }
                                ]
                            }""";
        String sensorData = """
                            {
                              "src": "shellyhtg3-84fce63ad204",
                              "dst": "ht-suite/events",
                              "method": "NotifyFullStatus",
                              "params": {
                                "ts": 1752192302.55,
                                "ble": {},
                                "cloud": {
                                  "connected": false
                                },
                                "devicepower:0": {
                                  "id": 0,
                                  "battery": {
                                    "V": 5.32,
                                    "percent": 65
                                  },
                                  "external": {
                                    "present": false
                                  }
                                },
                                "ht_ui": {},
                                "humidity:0": {
                                  "id": 0,
                                  "rh": 58.9
                                },
                                "mqtt": {
                                  "connected": true
                                },
                                "sys": {
                                  "mac": "84FCE63AD204",
                                  "restart_required": false,
                                  "time": null,
                                  "unixtime": null,
                                  "uptime": 1,
                                  "ram_size": 256644,
                                  "ram_free": 120584,
                                  "fs_size": 1048576,
                                  "fs_free": 774144,
                                  "cfg_rev": 14,
                                  "kvs_rev": 0,
                                  "webhook_rev": 0,
                                  "available_updates": {},
                                  "wakeup_reason": {
                                    "boot": "deepsleep_wake",
                                    "cause": "periodic"
                                  },
                                  "wakeup_period": 7200,
                                  "reset_reason": 8
                                },
                                "temperature:0": {
                                  "id": 0,
                                  "tC": 19.9,
                                  "tF": 67.8
                                },
                                "wifi": {
                                  "sta_ip": "192.168.1.81",
                                  "status": "got ip",
                                  "ssid": "IOTNET",
                                  "rssi": -68
                                },
                                "ws": {
                                  "connected": false
                                }
                              }
                            }""";
        //Saque el String de data Switch
        List<DataSwitch> switchStatus = new ArrayList<>();
        switchStatus.add(new DataSwitch(1, false));
        Controller instance = new ControllerImpl();
        List<Operacion> expResult = List.of(new Operacion(1, true));
        List<Operacion> result = instance.controlTemperatura(siteConfig, sensorData,switchStatus);
        
        //Sobrescribir equals y hashCode en Operacion
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getSwitchId());
        
        //Comparar manualmente los campos:
        assertTrue(result.get(0).isPower());
    }

    public class ControllerImpl implements Controller {

        @Override
        public List<Operacion> controlTemperatura(String siteConfig, String sensorData, List<DataSwitch> switchStatus) {
            
            DataSite dSite;
            DataSensor dSensor;
            List<DataSwitch> dSwitch;
            try {
                dSite = new DataSite(siteConfig);
                dSensor = new DataSensor(sensorData);
                dSwitch = switchStatus;
            } catch (JsonProcessingException ex) {
                System.getLogger(ControllerTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                return Collections.emptyList();
            } catch (Exception ex) {
                System.getLogger(ControllerTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                return Collections.emptyList();
            }
            
            List<Operacion> operaciones = new ArrayList<>();
            
            for (DataSwitch s : dSwitch) {
                for (Room r : dSite.getRooms()) {
                    if (dSensor.getSrcSensor().equals(r.getSrcSensor()) &&
                            r.getExpectedTemp() < dSensor.getTemperatura()) {
                        operaciones.add(new Operacion(s.getId(), true));
                    }
                }
            }      
            return operaciones;
        }
        
    }
    
}
