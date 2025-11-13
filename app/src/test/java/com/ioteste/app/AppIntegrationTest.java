package com.ioteste.app; // En el mismo paquete que AppTest.java

import com.ioteste.control.*; // Importamos las clases REALES
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AppIntegrationTest {

    private TestableApp app;
    private Controller mockController;
    private DataSite mockSiteConfig;
    private Context mockContext; // Objeto de contexto para reutilizar


    static class TestableApp extends AppTest { // <-- HEREDA DE AppTest
        
        public Map<String, String> simulatedPostCalls = new HashMap<>();
        public boolean simulateRestFailure = false;

        // 1. Mockeamos la carga de configuración
        @Override
        public DataSite loadSiteConfig() {
            System.out.println("TEST: loadSiteConfig() interceptado. Devolviendo config mock.");
            // Usamos el campo 'siteConfig' (ahora protected)
            return this.siteConfig;
        }

        // 2. Mockeamos la obtención de estado inicial
        @Override
        public List<DataSwitch> getInitialSwitchesStatus() {
            System.out.println("TEST: getInitialSwitchesStatus() interceptado. Devolviendo estado 'off'.");
            List<DataSwitch> initialStatus = new ArrayList<>();
            if (siteConfig != null) {
                for (Room room : siteConfig.getRooms()) {
                    initialStatus.add(new DataSwitch(room.getSwitchURL(), false));
                }
            }
            // Importante: seteamos el estado interno (ahora protected)
            this.knownSwitchStatus = initialStatus;
            return initialStatus;
        }

        // 3. Interceptamos el envío de comandos (POST)
        // Esto compila porque el método original en AppTest es 'protected'
        @Override
        protected String postSwitchOp(String switchURL, String jsonBody) throws IOException, InterruptedException {
            if (simulateRestFailure) {
                System.out.println("TEST: Simulando fallo REST para " + switchURL);
                throw new IOException("Simulated REST Communication Failure");
            }
            
            System.out.println("TEST: Interceptado POST a " + switchURL + " con body: " + jsonBody);
            simulatedPostCalls.put(switchURL, jsonBody);
            return "{\"status\": \"ack_mock\"}"; // Devolvemos un ACK simulado
        }
        
        // --- Métodos de ayuda para el Test ---
        
        // Hacemos público el manejador para simular un mensaje MQTT
        public void simulateMqttMessage(String topic, String payload) {
            System.out.println("\n--- SIMULANDO MENSAJE MQTT ---");
            System.out.println("Topic: " + topic);
            System.out.println("Payload: " + payload);
            super.handleSensorMessage(topic, payload);
        }
        
        // Usamos el método 'snapshotSwitchStatus' (ahora protected)
        public List<DataSwitch> getInternalSwitchState() {
            return super.snapshotSwitchStatus();
        }
    }


    @BeforeEach
    void setUp() {
        // 1. Creamos la configuración mock del sitio
        
        // **CORRECCIÓN: Usamos el constructor de 5 argumentos de Room**
        Room living = new Room("Living", 22.0f, 1500.0f, "http://switch/living-ac", "mqtt:sensor/living");
        Room dormitorio = new Room("Dormitorio", 21.0f, 1200.0f, "http://switch/dorm-ac", "mqtt:sensor/dormitorio");
        
        mockSiteConfig = mock(DataSite.class);
        when(mockSiteConfig.getRooms()).thenReturn(List.of(living, dormitorio));

        // 2. Creamos nuestra App "testeable"
        app = new TestableApp();
        
        // 3. Creamos el mock del Controller
        mockController = mock(Controller.class);
        
        // 4. Inyectamos las dependencias
        app.setController(mockController);
        // Esto ahora funciona porque 'siteConfig' es protected
        app.siteConfig = mockSiteConfig;
        
        // 5. Creamos un Context mock
        mockContext = new Context(LocalDateTime.now());
        
        // 6. Inicializamos el estado interno
        app.getInitialSwitchesStatus(); // Esto poblará app.knownSwitchStatus
    }

    /**
     * CASO 1: Control de temperatura cuando la carga total NO es una limitación.
     */
    @Test
    void testControl_NoLoadLimitation_TurnsOnSwitch() {
        // Arrange
        Operation turnOnLiving = new Operation("http://switch/living-ac", true);
        
        // **CORRECCIÓN: Usamos el constructor de 2 argumentos de ControlResponse**
        ControlResponse response = new ControlResponse(List.of(turnOnLiving), mockContext);
        
        when(mockController.powerManagement(any(AppData.class))).thenReturn(response);

        assertFalse(app.getInternalSwitchState().get(0).isActive());

        // Act
        // El topic debe coincidir con el ID del switch en la URL
        app.simulateMqttMessage("mqtt/sensor/living/living-ac", "{\"temperature\": 30.0}");

        // Assert
        assertEquals(1, app.simulatedPostCalls.size(), "Debería haberse enviado 1 comando");
        String expectedJson = "{\"state\": true}";
        assertEquals(expectedJson, app.simulatedPostCalls.get("http://switch/living-ac"));
        
        assertTrue(app.getInternalSwitchState().get(0).isActive(), "El estado interno del switch 'Living' debe ser 'true'");
    }

    /**
     * CASO 2: Control cuando la carga total ES una limitación efectiva.
     */
    @Test
    void testControl_WithLoadLimitation_ManagesSwitches() {
        // Arrange
        // Forzamos el estado inicial: "Living" está encendido
        app.knownSwitchStatus.get(0).setActive(true); // Funciona porque es protected
        assertTrue(app.getInternalSwitchState().get(0).isActive(), "Setup: 'Living' debe empezar encendido");
        assertFalse(app.getInternalSwitchState().get(1).isActive(), "Setup: 'Dormitorio' debe empezar apagado");

        Operation turnOffLiving = new Operation("http://switch/living-ac", false);
        Operation turnOnDormitorio = new Operation("http://switch/dorm-ac", true);
        
        // **CORRECCIÓN: Usamos el constructor de 2 argumentos de ControlResponse**
        ControlResponse response = new ControlResponse(List.of(turnOffLiving, turnOnDormitorio), mockContext);
        
        when(mockController.powerManagement(any(AppData.class))).thenReturn(response);

        // Act
        app.simulateMqttMessage("mqtt/sensor/dormitorio/dorm-ac", "{\"temperature\": 31.0}");

        // Assert
        assertEquals(2, app.simulatedPostCalls.size(), "Deberían haberse enviado 2 comandos");
        
        String expectedJsonOff = "{\"state\": false}";
        String expectedJsonOn = "{\"state\": true}";
        assertEquals(expectedJsonOff, app.simulatedPostCalls.get("http://switch/living-ac"), "Debe apagar el 'Living'");
        assertEquals(expectedJsonOn, app.simulatedPostCalls.get("http://switch/dorm-ac"), "Debe encender el 'Dormitorio'");
        
        assertFalse(app.getInternalSwitchState().get(0).isActive(), "Estado final 'Living' debe ser 'false'");
        assertTrue(app.getInternalSwitchState().get(1).isActive(), "Estado final 'Dormitorio' debe ser 'true'");
    }

    /**
     * CASO 3: Caso Borde - Falla en la comunicación REST.
     */
    @Test
    void testEdgeCase_RestCommunicationFailure() {
        // Arrange
        app.simulateRestFailure = true;

        Operation turnOnLiving = new Operation("http://switch/living-ac", true);
        
        // **CORRECCIÓN: Usamos el constructor de 2 argumentos de ControlResponse**
        ControlResponse response = new ControlResponse(List.of(turnOnLiving), mockContext);
        
        when(mockController.powerManagement(any(AppData.class))).thenReturn(response);

        assertFalse(app.getInternalSwitchState().get(0).isActive());

        // Act
        app.simulateMqttMessage("mqtt/sensor/living/living-ac", "{\"temperature\": 30.0}");

        // Assert
        assertEquals(0, app.simulatedPostCalls.size(), "Ningún comando POST debió completarse con éxito");

        // Verificamos el estado interno
        assertTrue(app.getInternalSwitchState().get(0).isActive(), 
            "El estado interno se actualiza INCLUSO si la red falla (comportamiento actual)");
    }
}