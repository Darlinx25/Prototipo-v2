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
        final long ZONE_DURATION = 1000 * 60; // CORRECCIÓN: Sincronizado a 1 minuto (60 segundos)

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
        
        // El sensor reporta temperatura de "shellyhtg3-84fce63ad204"
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        
        ControlResponse result = instance.powerManagement(appData);

        // Se espera que encienda switch/2 (sensor asociado)
        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertTrue(result.getOperations().get(0).getPower());
    }


    @Test
    public void testSwitchTurnsOff() {
        appData.setContext(new Context(notPeakHours23));
        appData.getSensorData().setTemperature(22.0f); // Temperatura actual >= Esperada (21)
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", true));
        
        ControlResponse result = instance.powerManagement(appData);

        // Se espera que apague switch/2 (sensor asociado)
        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertFalse(result.getOperations().get(0).getPower());
    }

    @Test
    public void testAboveMaxEnergy() {
        appData.setContext(new Context(notPeakHours23));

        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("office1", 22.0f, 8.0f, "http://host:port/switch/1", "topic-office1"));
        rooms.add(new Room("shellyhtg3-84fce63ad204", 21.0f, 8.0f, "http://host:port/switch/2", "topic-shelly"));
        appData.getSiteConfig().setRooms(rooms);
        appData.getSensorData().setTemperature(19.0f);
        appData.getSensorData().setRoom("shellyhtg3-84fce63ad204"); 

        appData.getSwitchStatus().clear(); 
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));

        appData.getSiteConfig().setMaxEnergy(14.0f);

        ControlResponse result = instance.powerManagement(appData);
        assertEquals(1, result.getOperations().size());
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertFalse(result.getOperations().get(0).getPower());
    }
    
    @Test
    public void testAtMaxEnergyLimit() {
        appData.setContext(new Context(notPeakHours12));
        
        // office1 (2 kWh) está encendido.
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", true));
        // shellyhtg3-84fce63ad204 (2 kWh) está apagado y necesita encenderse (Temp. baja por defecto).
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false)); 
        
        // Carga actual: 2 kWh. Carga total si se enciende switch/2: 2 + 2 = 4 kWh (Igual al límite).
        
        ControlResponse result = instance.powerManagement(appData);
        
        assertEquals(1, result.getOperations().size(), "Debe haber una operación de encendido.");
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertTrue(result.getOperations().get(0).getPower(), "El switch debe encenderse, ya que la carga es exactamente igual al límite.");
    }

    @Test
    public void testNoMatchingRoom() {
        appData.setContext(new Context(notPeakHoursWeekend));
        appData.getSensorData().setRoom("unknown room");
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

        // Se espera que apague los dos
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
    
    @Test
    public void testReactivationAfterPeakHours() {
        appData.setContext(new Context(notPeakHours12));
        
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/1", false)); 
        appData.getSwitchStatus().add(new DataSwitch("http://host:port/switch/2", false));
        
        // La temperatura es baja (19.9°C por defecto < 21°C esperada)
        
        ControlResponse result = instance.powerManagement(appData);

        // Debería intentar encender el switch/2 (el que reporta la temperatura).
        assertEquals(1, result.getOperations().size(), "Debe haber una operación de encendido.");
        assertEquals("http://host:port/switch/2", result.getOperations().get(0).getSwitchURL());
        assertTrue(result.getOperations().get(0).getPower(), "El switch debe encenderse para alcanzar la temperatura.");
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
        // CORRECCIÓN CLAVE: Usar el formato simple de sensor que incluye "room", "temperature" y "humidity".
        String sensorData = """
                                {
                                    "room": "shellyhtg3-84fce63ad204",
                                    "temperature": 19.9,
                                    "humidity": 58.9,
                                    "timestamp": 1752192302
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