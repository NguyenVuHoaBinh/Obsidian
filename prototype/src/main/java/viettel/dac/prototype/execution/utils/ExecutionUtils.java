package viettel.dac.prototype.execution.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for execution-related calculations and formatting.
 */
public class ExecutionUtils {

    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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
     * Simplified format with minutes and seconds.
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

    /**
     * Formats a duration in human-readable format with appropriate units.
     * Handles milliseconds up to days.
     *
     * @param millis The duration in milliseconds.
     * @return A formatted string representing the duration.
     */
    public static String formatDurationDetailed(long millis) {
        // For very short durations, show milliseconds
        if (millis < 1000) {
            return millis + " ms";
        }

        long seconds = millis / 1000;
        long ms = millis % 1000;

        // For durations less than a minute, show seconds with millisecond precision
        if (seconds < 60) {
            return String.format("%d.%03d sec", seconds, ms);
        }

        long minutes = seconds / 60;
        seconds %= 60;

        // For durations less than an hour, show minutes and seconds
        if (minutes < 60) {
            return String.format("%d min %d sec", minutes, seconds);
        }

        long hours = minutes / 60;
        minutes %= 60;

        // For durations less than a day, show hours and minutes
        if (hours < 24) {
            return String.format("%d hr %d min", hours, minutes);
        }

        // For longer durations, show days and hours
        long days = hours / 24;
        hours %= 24;

        return String.format("%d days %d hr", days, hours);
    }

    /**
     * Formats a timestamp using the default ISO formatter.
     *
     * @param timestamp The timestamp to format.
     * @return A formatted string representing the timestamp.
     */
    public static String formatTimestamp(LocalDateTime timestamp) {
        return timestamp.format(DEFAULT_FORMATTER);
    }

    /**
     * Formats a timestamp using a custom formatter.
     *
     * @param timestamp The timestamp to format.
     * @param formatter The formatter to use.
     * @return A formatted string representing the timestamp.
     */
    public static String formatTimestamp(LocalDateTime timestamp, DateTimeFormatter formatter) {
        return timestamp.format(formatter);
    }

    /**
     * Checks if an execution has timed out.
     *
     * @param startTime The start time.
     * @param timeoutMs The timeout in milliseconds.
     * @return True if the execution has timed out, false otherwise.
     */
    public static boolean hasTimedOut(LocalDateTime startTime, long timeoutMs) {
        return calculateDurationMillis(startTime, LocalDateTime.now()) > timeoutMs;
    }

    /**
     * Converts a duration in milliseconds to the specified time unit.
     *
     * @param millis The duration in milliseconds.
     * @param unit The target time unit.
     * @return The duration in the specified unit.
     */
    public static long convertDuration(long millis, TimeUnit unit) {
        return unit.convert(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates execution time statistics from an array of execution times.
     *
     * @param executionTimes Array of execution times in milliseconds.
     * @return An array containing [min, max, avg, total] times in milliseconds.
     */
    public static double[] calculateExecutionStatistics(long[] executionTimes) {
        if (executionTimes == null || executionTimes.length == 0) {
            return new double[] {0, 0, 0, 0};
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long total = 0;

        for (long time : executionTimes) {
            min = Math.min(min, time);
            max = Math.max(max, time);
            total += time;
        }

        double avg = (double) total / executionTimes.length;

        return new double[] {min, max, avg, total};
    }

    /**
     * Estimates remaining time based on completed work and average time per unit.
     *
     * @param completedUnits Number of completed units of work.
     * @param totalUnits Total number of units of work.
     * @param elapsedTimeMs Time elapsed so far in milliseconds.
     * @return Estimated remaining time in milliseconds.
     */
    public static long estimateRemainingTime(int completedUnits, int totalUnits, long elapsedTimeMs) {
        if (completedUnits <= 0) {
            return -1; // Cannot estimate
        }

        double avgTimePerUnit = (double) elapsedTimeMs / completedUnits;
        int remainingUnits = totalUnits - completedUnits;

        return Math.round(avgTimePerUnit * remainingUnits);
    }

    /**
     * Combines multiple execution times into a total and validates timeout.
     *
     * @param executionTimes Array of execution times in milliseconds.
     * @param timeoutMs Maximum allowed total time in milliseconds.
     * @return Total execution time in milliseconds.
     * @throws IllegalStateException if the total time exceeds the timeout.
     */
    public static long combinedExecutionTime(long[] executionTimes, long timeoutMs) {
        long total = 0;

        for (long time : executionTimes) {
            total += time;

            if (total > timeoutMs) {
                throw new IllegalStateException(
                        "Combined execution time " + formatDurationDetailed(total) +
                                " exceeds timeout of " + formatDurationDetailed(timeoutMs)
                );
            }
        }

        return total;
    }
}