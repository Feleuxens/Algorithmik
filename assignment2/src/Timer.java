import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for timing operations.
 */
public class Timer {
    private static final Map<String, Long> startTimes = new HashMap<>();
    private static final Map<String, Long> elapsedTimes = new HashMap<>();

    /**
     * Start timing an operation.
     *
     * @param operation the operation name
     */
    public static void startTiming(String operation) {
        startTimes.put(operation, System.currentTimeMillis());
    }

    /**
     * Stop timing an operation and record elapsed time.
     *
     * @param operation the operation name
     * @return the elapsed time in milliseconds
     */
    public static long stopTiming(String operation) {
        if (!startTimes.containsKey(operation)) {
            return 0;
        }

        long startTime = startTimes.get(operation);
        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        elapsedTimes.put(operation, elapsed);
        System.out.println(operation + " completed in " + elapsed + " ms");

        return elapsed;
    }

    /**
     * Get the elapsed time for an operation.
     *
     * @param operation the operation name
     * @return the elapsed time in milliseconds, or 0 if not recorded
     */
    public static long getElapsedTime(String operation) {
        return elapsedTimes.getOrDefault(operation, 0L);
    }

    /**
     * Reset all timers.
     */
    public static void reset() {
        startTimes.clear();
        elapsedTimes.clear();
    }
}