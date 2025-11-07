/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */
package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class DataSensorTest {
    
    public DataSensorTest() {
        
        
    }

    @Test
    public void testDataSensor() {
        String sensor1= """
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
        DataSensor test1;
        try{
        test1 = new DataSensor(sensor1);
        }catch(JsonProcessingException x){
            System.getLogger(DataSensorTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, x);
            return;
        }
        assertEquals("shellyhtg3-84fce63ad204", test1.getSrc());
        assertEquals(19.9f , test1.getTemperature());
        long timeMillisec = (long) (1752192302.55 * 1000);
        
        long epochMilli = test1.getDateTime().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();


        assertEquals(timeMillisec, epochMilli);
        
        
        String sensor2= """
                            {
                              "src": "Prueba sensor 2",
                              "dst": "ht-suite/events",
                              "method": "NotifyFullStatus",
                              "params": {
                                "ts": 5,
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
                                  "tC": 5,
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
        DataSensor test2;
        try{
        test2 = new DataSensor(sensor2);
        }catch(JsonProcessingException x){
            System.getLogger(DataSensorTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, x);
            return;
        }
        assertEquals("Prueba sensor 2", test2.getSrc());
        assertEquals(5f , test2.getTemperature());
        long timeMillisec1 = (long) (5 * 1000);
        
        long epochMilli1 = test2.getDateTime().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();


        assertEquals(timeMillisec1, epochMilli1);
    }
    
    
}
