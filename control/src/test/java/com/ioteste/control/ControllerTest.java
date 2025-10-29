package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
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
        notPeakHours12 = LocalDateTime.of(2025, Month.NOVEMBER, 3, 12, 0, 0);
        notPeakHours23 = LocalDateTime.of(2025, Month.NOVEMBER, 3, 23, 0, 0);
        notPeakHoursWeekend = LocalDateTime.of(2025, Month.NOVEMBER, 2, 17, 0, 0);
        peakHours17 = LocalDateTime.of(2025, Month.NOVEMBER, 3, 17, 0, 0);
        peakHours22 = LocalDateTime.of(2025, Month.NOVEMBER, 3, 22, 59, 59);
    }
    
    @BeforeEach
    void init() {
        appData = getAppDataTemplate();
        instance = new ControllerImpl();
        
    }
    
    /*prender switch si la temperatura es menor que la esperada*/
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
    
    
    /*apagar switch si la temperatura es mayor o igual que la esperada*/
    @Test
    public void testSwitchTurnsOff() {
        appData.setContext(new Context(notPeakHours12));
        appData.getSensorData().setTemperature(22);
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", true));
        ControlResponse result = instance.powerManagement(appData);
        
        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertFalse(result.getOperations().get(0).getPower());
    }
    
    /*no prender switch aunque la temperatura sea baja si esto causa un apagón*/
    @Test
    public void testAboveMaxEnergy() {
        appData.setContext(new Context(notPeakHours23));
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("office1", 22, 8, "http://host:port/switch/1"));
        rooms.add(new Room("shellyhtg3-84fce63ad204", 21, 8, "http://host:port/switch/2"));
        appData.getSiteConfig().setRooms(rooms);
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        ControlResponse result = instance.powerManagement(appData);
        
        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertFalse(result.getOperations().get(0).getPower());
    }
    
    /*no operaciones si el sensor src no corresponde a ningún room*/
    @Test
    public void testNoMatchingRoom() {
        appData.setContext(new Context(notPeakHoursWeekend));
        appData.getSensorData().setSrc("unknown room");
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        ControlResponse result = instance.powerManagement(appData);

        assertTrue(result.getOperations().isEmpty());
    }
    
    /*apagar los switches en horario punta*/
    @Test
    public void testPeakHours() {
        appData.setContext(new Context(peakHours17));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", true));
        ControlResponse result = instance.powerManagement(appData);
        
        assertEquals(2, result.getOperations().size());
        assertFalse(result.getOperations().get(0).getPower());
        assertFalse(result.getOperations().get(1).getPower());
        
        
        appData.setContext(new Context(peakHours22));
        result = instance.powerManagement(appData);
        
        assertEquals(2, result.getOperations().size());
        assertFalse(result.getOperations().get(0).getPower());
        assertFalse(result.getOperations().get(1).getPower());
    }
    
    
    public class ControllerImpl implements Controller {

        @Override
        public ControlResponse powerManagement(AppData appData) {
            DataSite siteConfig = appData.getSiteConfig();
            DataSensor sensorData = appData.getSensorData();
            List<DataSwitch> switchStatus = appData.getSwitchStatus();
            Context context = appData.getContext();
            
            List<Operation> operations = new ArrayList<>();
            
            if (peakHours(context.getCurrentTime())) {
                for (DataSwitch s : switchStatus) {
                    if (s.isActive()) {
                        operations.add(new Operation(s.getSwitchURL(), false));
                    }
                }
                return new ControlResponse(operations, context);
            }
            
            float currentEnergy = getCurrentEnergy(siteConfig, switchStatus);
            
            for (Room r : siteConfig.getRooms()) {
                boolean isActiveSwitch = isActiveRoomSwitch(r, switchStatus);
                
                if (sensorData.getSrc().equals(r.getName())) {
                    if (!isActiveSwitch && currentEnergy + r.getEnergy() > siteConfig.getMaxEnergy()) {
                        boolean power = false;
                        operations.add(new Operation(r.getSwitchURL(), power));
                    } else {
                        boolean power = sensorData.getTemperature() < r.getExpectedTemp();
                        operations.add(new Operation(r.getSwitchURL(), power));
                    }
                }  
            }
            return new ControlResponse(operations, context);
        }
        
        private float getCurrentEnergy(DataSite siteConfig, List<DataSwitch> switchStatus) {
            float currentEnergy = 0;
            
            for (Room r : siteConfig.getRooms()) {
                for (DataSwitch dSwitch : switchStatus) {
                    if (dSwitch.isActive() && dSwitch.getSwitchURL().equals(r.getSwitchURL())) {
                        currentEnergy += r.getEnergy();
                    }
                }
            }
            
            return currentEnergy;
        }
        
        private boolean isActiveRoomSwitch(Room r, List<DataSwitch> switchStatus) {
            for (DataSwitch dSwitch : switchStatus) {
                if (r.getSwitchURL().equals(dSwitch.getSwitchURL())) {
                    return dSwitch.isActive();
                }
            }
            return true;
        }
        
        private boolean peakHours(LocalDateTime currentTime) {
            boolean weekend = currentTime.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    currentTime.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend || currentTime.getHour() < 17 || currentTime.getHour() == 23) {
                return false;
            } else {
                return true;
            }
        }
        
    }
    
    private AppData getAppDataTemplate() {
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
