package com.pidabrow.starter.common.util;

/**
 * Common utility class for string operations.
 * Placeholder for shared utilities across all microservices.
 */
public class StringUtils {

    private StringUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Checks if a string is null or empty.
     *
     * @param str the string to check
     * @return true if the string is null or empty, false otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if a string is not null and not empty.
     *
     * @param str the string to check
     * @return true if the string is not null and not empty, false otherwise
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
