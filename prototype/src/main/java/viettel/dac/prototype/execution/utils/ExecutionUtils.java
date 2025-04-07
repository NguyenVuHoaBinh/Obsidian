package viettel.dac.prototype.execution.utils;


import java.time.Duration;
import java.time.LocalDateTime;

public class ExecutionUtils {

    /**
     * Calculates the duration between two timestamps in milliseconds.
     *
     * @param start The start time.
     * @param end   The end time.
     * @return The duration in milliseconds.
     */
    public static long calculateDurationMillis(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toMillis();
    }

    /**
     * Formats a duration in milliseconds into a human-readable format.
     *
     * @param millis The duration in milliseconds.
     * @return A formatted string representing the duration.
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;

        if (minutes > 0) {
            return String.format("%d min %d sec", minutes, seconds);
        } else {
            return String.format("%d sec", seconds);
        }
    }
}

