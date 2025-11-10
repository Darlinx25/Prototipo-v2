// energycost.java
package com.ioteste.control;

/**
 * Informa Costo de la Energía y cuando cambia de uno costo a otro.
 */
public class EnergyCost {

    public final static String TEST_CONTRACT_30S = "testContract";
    public final static int LOW = 0;
    public final static int HIGH = 1;
    private final static long ZONE_DURATION = 1000 * 60 * 30;

    public record EnergyZone(int current, int next, long nextTS) {
    }

    public static EnergyZone currentEnergyZone(String contract) {
        return energyZone(contract, System.currentTimeMillis());
    }

    public static EnergyZone energyZone(String contract, long ts) {
        if (TEST_CONTRACT_30S.equals(contract)) {
            long base = ts / ZONE_DURATION;
            int zone = (int) (base % 2);
            int nextZone = (zone + 1) % 2;
            long nextZoneTS = (base + 1) * ZONE_DURATION;

            return new EnergyZone(zone, nextZone, nextZoneTS);
        } else {
            throw new IllegalArgumentException("Invalid contract value: " + contract);
        }
    }
}