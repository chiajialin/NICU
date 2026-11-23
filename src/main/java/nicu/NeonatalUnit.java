package nicu;

import java.util.Random;

/**
 * Simulated interface to the neonatal unit hardware.
 *
 * Cot IDs:
 *   0 Incubator 1    1 Incubator 2    2 Incubator 3    3 Incubator 4
 *   4 Warmer 1       5 Warmer 2
 *
 * Cots 0-3 are closed incubators (temperature + humidity control).
 * Cots 4-5 are open radiant warmers (temperature control only - no humidity sensor or humidifier).
 *
 * The simulation updates itself in the background every 200 ms.
 */
public class NeonatalUnit {

    private static final int COTS = 6;
    private static final int FIRST_WARMER = 4;

    private final double[] temperature = {36.2, 36.6, 37.1, 36.4, 36.0, 36.9};
    private final double[] humidity = {58, 72, 66, 81, 0, 0};
    private final double[] spo2 = {96, 94, 97, 95, 93, 98};
    private final double[] heartRate = {145, 150, 138, 160, 142, 155};
    private final boolean[] heaterOn = new boolean[COTS];
    private final boolean[] humidifierOn = new boolean[COTS];

    // short-lived physiological excursions (ticks remaining)
    private final int[] desatTicks = new int[COTS];
    private final int[] hrTicks = new int[COTS];
    private final double[] hrOffset = new double[COTS];
    // sensor disconnection (ticks remaining)
    private final int[] faultTicks = new int[COTS];

    private final Random rnd = new Random();
    private final String[] statuses = {
            "Nurse station: all staff on unit",
            "Nurse station: 1 nurse on break",
            "Nurse station: consultant ward round in progress",
            "Nurse station: admission expected from labour ward",
            "Nurse station: pharmacy delivery awaited"
    };

    public NeonatalUnit() {
        java.util.Timer t = new java.util.Timer("NICU-simulator", true);
        t.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                step();
            }
        }, 200, 200);
    }

    private synchronized void step() {
        for (int c = 0; c < COTS; c++) {
            boolean warmer = c >= FIRST_WARMER;
            // temperature: rises with heater on, falls with it off. Warmers lose heat faster.
            if (heaterOn[c]) temperature[c] += 0.02 + rnd.nextDouble() * 0.01;
            else temperature[c] -= (warmer ? 0.025 : 0.015) + rnd.nextDouble() * 0.01;
            // portholes opened / baby handled: sudden drop
            if (rnd.nextDouble() < 0.002) temperature[c] -= 0.4 + rnd.nextDouble() * 0.4;

            if (!warmer) {
                if (humidifierOn[c]) humidity[c] += 0.4 + rnd.nextDouble() * 0.2;
                else humidity[c] -= 0.2 + rnd.nextDouble() * 0.2;
                if (rnd.nextDouble() < 0.002) humidity[c] -= 8; // porthole opened
                humidity[c] = Math.max(20, Math.min(99, humidity[c]));
            }

            // SpO2 wanders 92-99, with occasional desaturation episodes
            if (desatTicks[c] > 0) {
                desatTicks[c]--;
                spo2[c] = Math.max(78, spo2[c] - 0.8 + rnd.nextDouble() * 0.5);
            } else {
                spo2[c] += (rnd.nextDouble() - 0.5) * 0.6 + (96 - spo2[c]) * 0.05;
                if (rnd.nextDouble() < 0.0015) desatTicks[c] = 15 + rnd.nextInt(20);
            }
            spo2[c] = Math.min(100, spo2[c]);

            // heart rate wanders 120-170, with occasional brady/tachycardia
            if (hrTicks[c] > 0) {
                hrTicks[c]--;
            } else {
                hrOffset[c] *= 0.8;
                if (rnd.nextDouble() < 0.0015) {
                    hrTicks[c] = 15 + rnd.nextInt(20);
                    hrOffset[c] = rnd.nextBoolean() ? -60 : 45;
                }
            }
            heartRate[c] += (rnd.nextDouble() - 0.5) * 3 + (148 - heartRate[c]) * 0.05;

            // sensor leads occasionally come off for a few seconds
            if (faultTicks[c] > 0) faultTicks[c]--;
            else if (rnd.nextDouble() < 0.0015) faultTicks[c] = 10 + rnd.nextInt(25);
        }
    }

    private void checkCot(int cotID) {
        if (cotID < 0 || cotID >= COTS)
            throw new IllegalArgumentException("No such cot: " + cotID);
    }

    private void checkIncubator(int cotID) {
        checkCot(cotID);
        if (cotID >= FIRST_WARMER)
            throw new IllegalArgumentException("Cot " + cotID + " is a radiant warmer and has no humidity sensor or humidifier");
    }

    private void checkFault(int cotID, String sensor) throws SensorException {
        if (faultTicks[cotID] > 0)
            throw new SensorException(sensor + " sensor on cot " + cotID + " not responding - check lead");
    }

    /** Skin temperature in degrees centigrade. */
    public synchronized double getTemperature(int cotID) throws SensorException {
        checkCot(cotID);
        checkFault(cotID, "Temperature");
        return temperature[cotID];
    }

    /** Relative humidity in percent. Incubators only. */
    public synchronized int getHumidity(int cotID) throws SensorException {
        checkIncubator(cotID);
        checkFault(cotID, "Humidity");
        return (int) Math.round(humidity[cotID]);
    }

    /** Blood oxygen saturation in percent. */
    public synchronized int getSpO2(int cotID) throws SensorException {
        checkCot(cotID);
        checkFault(cotID, "Pulse oximeter");
        return (int) Math.round(spo2[cotID]);
    }

    /** Heart rate in beats per minute. */
    public synchronized int getHeartRate(int cotID) throws SensorException {
        checkCot(cotID);
        checkFault(cotID, "Pulse oximeter");
        return (int) Math.round(heartRate[cotID] + hrOffset[cotID]);
    }

    public synchronized void setHeater(int cotID, boolean on) {
        checkCot(cotID);
        heaterOn[cotID] = on;
    }

    public synchronized boolean isHeaterOn(int cotID) {
        checkCot(cotID);
        return heaterOn[cotID];
    }

    public synchronized void setHumidifier(int cotID, boolean on) {
        checkIncubator(cotID);
        humidifierOn[cotID] = on;
    }

    public synchronized boolean isHumidifierOn(int cotID) {
        checkIncubator(cotID);
        return humidifierOn[cotID];
    }

    public synchronized String getNurseStationStatus() {
        return statuses[rnd.nextInt(statuses.length)];
    }
}
