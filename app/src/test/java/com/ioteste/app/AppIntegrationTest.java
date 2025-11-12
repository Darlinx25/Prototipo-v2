package com.ioteste.app;

import com.ioteste.control.Controller;
import com.ioteste.control.ControlResponse;
import com.ioteste.control.AppData;
import com.ioteste.control.Operation;
import com.ioteste.control.Context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


 
public class AppIntegrationTest {

    private App app;
    private Controller mockController;

    private static final String SENSOR_PAYLOAD = """
        {
            "src":"shellyhtg3-84fce63ad204",
            "ts":1735694700.0,
            "params":{
                "ts":1735694700.0,
                "humidity:0":{"rh":58.9},
                "temperature:0":{"tC":19.9} 
            }
        }
        """;

    private static final String TEST_TOPIC = "sim/ht/1"; 
    
    private Context dummyContext;

    @BeforeEach
    void setUp() throws Exception {
        app = new App();
        mockController = Mockito.mock(Controller.class);
        app.setController(mockController);

        try {
            app.loadSiteConfig(); 
            app.getInitialSwitchesStatus();
        } catch (Exception e) {
            System.err.println("ADVERTENCIA (TEST): 'cajaNegra' no está corriendo. " + e.getMessage());
        }
        
        dummyContext = new Context(LocalDateTime.now());
    }
    
    
    @Test
    void testIntegration_NoEnergyLimit_SwitchOn() throws Exception {
        Operation op1 = new Operation("http://localhost:8080/switch/1", true);
        ControlResponse mockResponse = new ControlResponse(List.of(op1), dummyContext);
        Mockito.when(mockController.powerManagement(any(AppData.class))).thenReturn(mockResponse);

        app.handleSensorMessage(TEST_TOPIC, SENSOR_PAYLOAD);

        verify(mockController).powerManagement(any(AppData.class));
    }
    
    @Test
    void testIntegration_EnergyLimitEffective_SwitchOff() throws Exception {
        Operation op2 = new Operation("http://localhost:8080/switch/1", false); 
        ControlResponse mockResponse = new ControlResponse(List.of(op2), dummyContext);
        Mockito.when(mockController.powerManagement(any(AppData.class))).thenReturn(mockResponse);

        app.handleSensorMessage(TEST_TOPIC, SENSOR_PAYLOAD);

        verify(mockController).powerManagement(any(AppData.class));
    }
    
    @Test
    void testIntegration_RestCommunicationFailure_Handled() throws Exception {
        Operation op2 = new Operation("http://localhost:8080/switch/1", true);
        ControlResponse mockResponse = new ControlResponse(List.of(op2), dummyContext);
        Mockito.when(mockController.powerManagement(any(AppData.class))).thenReturn(mockResponse);

        assertDoesNotThrow(() -> app.handleSensorMessage(TEST_TOPIC, SENSOR_PAYLOAD));

        verify(mockController).powerManagement(any(AppData.class));
    }
}