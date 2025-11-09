package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class ControllerTest {

    public ControllerTest() {
    }

    private AppData appData;
    private Controller instance;
    private static LocalDateTime notPeakHours12;
    private static LocalDateTime notPeakHours23;
    private static LocalDateTime notPeakHoursWeekend;
    private static LocalDateTime peakHours17;
    private static LocalDateTime peakHours22;

    @BeforeAll
    public static void setup() {
        long now = System.currentTimeMillis();
        final long ZONE_DURATION = 1000 * 60 * 30; 

        long currentZoneBaseTS = now / ZONE_DURATION * ZONE_DURATION;
        
        int currentZone = (int) ((currentZoneBaseTS / ZONE_DURATION) % 2);

        if (currentZone == EnergyCost.LOW) {
            notPeakHours12 = LocalDateTime.ofEpochSecond(currentZoneBaseTS / 1000, 0, ZoneOffset.UTC);
            peakHours17 = LocalDateTime.ofEpochSecond((currentZoneBaseTS + ZONE_DURATION) / 1000, 0, ZoneOffset.UTC);

        } else {
            peakHours17 = LocalDateTime.ofEpochSecond(currentZoneBaseTS / 1000, 0, ZoneOffset.UTC);
            notPeakHours12 = LocalDateTime.ofEpochSecond((currentZoneBaseTS + ZONE_DURATION) / 1000, 0, ZoneOffset.UTC);
        }
        
        notPeakHours23 = notPeakHours12.plusSeconds(10);
        notPeakHoursWeekend = notPeakHours12.plusSeconds(20);
        peakHours22 = peakHours17.plusSeconds(10);
    }

    @BeforeEach
    void init() {
        appData = getAppDataTemplate();
        instance = new DefaultController();

    }

    @Test
    public void testSwitchTurnsOn() {
        appData.setContext(new Context(notPeakHours12));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        ControlResponse result = instance.powerManagement(appData);

        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertTrue(result.getOperations().get(0).getPower());
    }


    @Test
    public void testSwitchTurnsOff() {
        appData.setContext(new Context(notPeakHours23));
        appData.getSensorData().setTemperature(22);
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", true));
        ControlResponse result = instance.powerManagement(appData);

        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertFalse(result.getOperations().get(0).getPower());
    }

    @Test
    public void testAboveMaxEnergy() {
        appData.setContext(new Context(notPeakHours23));
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("office1", 22, 8, "http://host:port/switch/1"));
        appData.getSensorData().setTemperature(19);
        rooms.add(new Room("shellyhtg3-84fce63ad204", 21, 8, "http://host:port/switch/2")); 
        appData.getSiteConfig().setRooms(rooms);
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        appData.getSiteConfig().setMaxEnergy(14);
        ControlResponse result = instance.powerManagement(appData);

        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertFalse(result.getOperations().get(0).getPower()); 
    }

    @Test
    public void testNoMatchingRoom() {
        appData.setContext(new Context(notPeakHoursWeekend));
        appData.getSensorData().setSrc("unknown room");
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        ControlResponse result = instance.powerManagement(appData);

        assertTrue(result.getOperations().isEmpty());
    }

    @Test
    public void testPeakHours() {
        appData.setContext(new Context(peakHours17));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", true));
        ControlResponse result1 = instance.powerManagement(appData);

        assertEquals(2, result1.getOperations().size());
        assertFalse(result1.getOperations().get(0).getPower());
        assertFalse(result1.getOperations().get(1).getPower());


        appData.setContext(new Context(peakHours22));
        appData.getSwitchStatus().clear();
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", true));
        ControlResponse result2 = instance.powerManagement(appData);

        assertEquals(2, result2.getOperations().size());
        assertFalse(result2.getOperations().get(0).getPower());
        assertFalse(result2.getOperations().get(1).getPower());
    }


    private AppData getAppDataTemplate() {
        String siteConfig = """
                            {
                                "site": "oficina001",
                                "maxEnergy": "4 kWh", 
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
                                        "sensor": "mqtt:topic1"
                                    },
                                    {
                                        "name": "shellyhtg3-84fce63ad204",
                                        "expectedTemp": "21",
                                        "energy": "2 kWh",
                                        "switch": "http://host:port/switch/2",
                                        "sensor": "mqtt:topic2"
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
        DataSite dSite;
        DataSensor dSensor;
        List<DataSwitch> dSwitch = new ArrayList<>();
        Context context = new Context(LocalDateTime.now());

        AppData appData;

        try {
            dSite = new DataSite(siteConfig);
            dSensor = new DataSensor(sensorData);
            appData = new AppData(dSite, dSensor, dSwitch, context);
            return appData;
        } catch (JsonProcessingException ex) {
            System.getLogger(ControllerTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return null;
        }
    }

}