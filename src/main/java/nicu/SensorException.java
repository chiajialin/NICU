package nicu;

/**
 * Thrown when a cot sensor cannot be read (lead disconnected, probe fault, etc).
 * This is a checked exception.
 */
public class SensorException extends Exception {
    public SensorException(String message) {
        super(message);
    }
}
