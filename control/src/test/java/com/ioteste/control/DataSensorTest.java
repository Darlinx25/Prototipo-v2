package com.ioteste.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
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
                                    "room": "shellyhtg3-84fce63ad204",
                                    "temperature": 19.9,
                                    "humidity": 58.9,
                                    "timestamp": 1752192302
                                }""";
        DataSensor test1;
        try{
            test1 = new DataSensor(sensor1);
        }catch(JsonProcessingException x){
            fail("Error al parsear JSON del sensor 1: " + x.getMessage());
            return;
        }
        
        assertEquals("shellyhtg3-84fce63ad204", test1.getRoom());
        assertEquals(19.9f , test1.getTemperature(), 0.01f); 
        
        long expectedEpochSeconds = 1752192302L;
        
        long expectedEpochMilli = expectedEpochSeconds * 1000L;
        long actualEpochMilli = test1.getDateTime().toEpochSecond(ZoneOffset.UTC) * 1000L; 

        assertEquals(expectedEpochMilli, actualEpochMilli, "El timestamp extraído debe coincidir con el valor fijo del JSON."); 
        
        
        String sensor2= """
                                {
                                    "room": "Test sensor 2",
                                    "temperature": 5.0,
                                    "humidity": 40.0,
                                    "timestamp": 5
                                }""";
        DataSensor test2;
        try{
            test2 = new DataSensor(sensor2);
        }catch(JsonProcessingException x){
            fail("Error al parsear JSON del sensor 2: " + x.getMessage());
            return;
        }
        
        assertEquals("Test sensor 2", test2.getRoom());
        assertEquals(5f , test2.getTemperature(), 0.01f); 
        
        long expectedEpochSeconds2 = 5L;
        long expectedEpochMilli2 = expectedEpochSeconds2 * 1000L;
        long actualEpochMilli2 = test2.getDateTime().toEpochSecond(ZoneOffset.UTC) * 1000L;

        assertEquals(expectedEpochMilli2, actualEpochMilli2, "El timestamp extraído debe coincidir con el valor fijo del JSON.");
    }
}