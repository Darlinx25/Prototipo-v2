package com.ioteste.app;

import com.ioteste.control.Controller;
import com.ioteste.control.ControlResponse;
import com.ioteste.control.AppData;
import com.ioteste.control.Operation;
import com.ioteste.control.Context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

public class AppIntegrationTest {

    private App app;
    private Controller mockController;

    private static final String SENSOR_PAYLOAD = 
        "{\"deviceId\":\"topic1\",\"room\":\"office1\",\"temperature\":20.5,\"humidity\":60.0,\"timestamp\":1700000000}";
    
    private Context dummyContext;

    @BeforeEach
    void setUp() throws Exception {
        app = new App();
        mockController = Mockito.mock(Controller.class);
        app.setController(mockController);
        
        app.loadSiteConfig(); 
        
        app.getInitialSwitchesStatus();
        
        dummyContext = new Context(LocalDateTime.now());
    }
    
    @Test
    void testIntegration_NoEnergyLimit_SwitchOn() throws Exception {
        Operation op1 = new Operation("http://host:port/switch/1", true);
        
        ControlResponse mockResponse = new ControlResponse(List.of(op1), dummyContext);

        Mockito.when(mockController.powerManagement(any(AppData.class)))
               .thenReturn(mockResponse);

        app.handleSensorMessage(SENSOR_PAYLOAD);

        verify(mockController).powerManagement(any(AppData.class));
    }
    
    @Test
    void testIntegration_EnergyLimitEffective_SwitchOff() throws Exception {
        Operation op2 = new Operation("http://host:port/switch/2", false); 
        
        ControlResponse mockResponse = new ControlResponse(List.of(op2), dummyContext);

        Mockito.when(mockController.powerManagement(any(AppData.class)))
               .thenReturn(mockResponse);

        app.handleSensorMessage(SENSOR_PAYLOAD);

        verify(mockController).powerManagement(any(AppData.class));
    }
    
    @Test
    void testIntegration_RestCommunicationFailure_Handled() throws Exception {
        Operation op2 = new Operation("http://host:port/switch/2", true);
        
        ControlResponse mockResponse = new ControlResponse(List.of(op2), dummyContext);

        Mockito.when(mockController.powerManagement(any(AppData.class)))
               .thenReturn(mockResponse);

        assertDoesNotThrow(() -> app.handleSensorMessage(SENSOR_PAYLOAD));

        verify(mockController).powerManagement(any(AppData.class));
    }
}