package com.ioteste.control;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DefaultController implements Controller {

    private boolean isPeakHours(LocalDateTime currentTime) {
        boolean isWeekend = currentTime.getDayOfWeek() == DayOfWeek.SATURDAY ||
                            currentTime.getDayOfWeek() == DayOfWeek.SUNDAY;
        int hour = currentTime.getHour();
        
        return !isWeekend && hour >= 17 && hour < 23;
    }

    private float getCurrentEnergy(DataSite siteConfig, List<DataSwitch> switchStatus) {
        float currentEnergy = 0;
        
        for (Room room : siteConfig.getRooms()) {
            for (DataSwitch dSwitch : switchStatus) {
                if (dSwitch.isActive() && dSwitch.getSwitchURL().equals(room.getSwitchURL())) {
                    currentEnergy += room.getEnergy();
                    break;
                }
            }
        }
        return currentEnergy;
    }

    private boolean isActiveRoomSwitch(Room room, List<DataSwitch> switchStatus) {
        for (DataSwitch dSwitch : switchStatus) {
            if (room.getSwitchURL().equals(dSwitch.getSwitchURL())) {
                return dSwitch.isActive();
            }
        }
        return false;
    }

    @Override
    public ControlResponse powerManagement(AppData appData) {
        DataSite siteConfig = appData.getSiteConfig();
        DataSensor sensorData = appData.getSensorData();
        List<DataSwitch> switchStatus = appData.getSwitchStatus();
        Context context = appData.getContext();
        
        List<Operation> operations = new ArrayList<>();
        
        if (isPeakHours(context.getCurrentTime())) {
            for (DataSwitch s : switchStatus) {
                if (s.isActive()) {
                    operations.add(new Operation(s.getSwitchURL(), false));
                }
            }
            return new ControlResponse(operations, context);
        }
        
        float currentEnergy = getCurrentEnergy(siteConfig, switchStatus);
        
        for (Room room : siteConfig.getRooms()) {
            if (sensorData.getSrc().equals(room.getName())) {
                
                boolean isActiveSwitch = isActiveRoomSwitch(room, switchStatus);
                boolean desiredPower = sensorData.getTemperature() < room.getExpectedTemp();
                
                if (desiredPower && !isActiveSwitch) {
                    if (currentEnergy + room.getEnergy() <= siteConfig.getMaxEnergy()) {
                        operations.add(new Operation(room.getSwitchURL(), true));
                    } else {
                        operations.add(new Operation(room.getSwitchURL(), false));
                    }
                    
                }
                else if (!desiredPower && isActiveSwitch) {
                    operations.add(new Operation(room.getSwitchURL(), false));
                }
                return new ControlResponse(operations, context);
            }
        }
        return new ControlResponse(operations, context);
    }
}
