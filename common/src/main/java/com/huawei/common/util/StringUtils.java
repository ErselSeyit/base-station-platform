package com.huawei.common.util;

/**
 * String utility methods for common operations across services.
 *
 * <p>Provides consistent implementations for string manipulation tasks
 * such as truncation and masking for secure logging.
 */
public final class StringUtils {

    /**
     * Default suffix appended when truncating strings.
     */
    public static final String DEFAULT_TRUNCATION_SUFFIX = "...";

    /**
     * Minimum token length required for masking (shorter tokens are fully masked).
     */
    private static final int TOKEN_MIN_LENGTH_FOR_MASKING = 10;

    /**
     * Number of characters to show at the start of a masked token.
     */
    private static final int TOKEN_PREFIX_LENGTH = 6;

    /**
     * Number of characters to show at the end of a masked token.
     */
    private static final int TOKEN_SUFFIX_LENGTH = 4;

    /**
     * Mask string used in the middle of masked tokens.
     */
    private static final String TOKEN_MASK = "...";

    // Prevent instantiation
    private StringUtils() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Truncates a string to the specified maximum length.
     *
     * <p>If the string exceeds maxLength, it is truncated without suffix.
     *
     * @param value     the string to truncate (may be null)
     * @param maxLength the maximum length
     * @return truncated string, or null if input was null
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * Truncates a string to the specified maximum length with a suffix.
     *
     * <p>If the string exceeds maxLength, it is truncated and the suffix is appended.
     * The total length will be maxLength + suffix.length().
     *
     * @param value     the string to truncate (may be null)
     * @param maxLength the maximum length before suffix
     * @param suffix    the suffix to append (e.g., "...")
     * @return truncated string with suffix, or null if input was null
     */
    public static String truncateWithSuffix(String value, int maxLength, String suffix) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + (suffix != null ? suffix : "");
    }

    /**
     * Masks a token for secure logging, showing only prefix and suffix.
     *
     * <p>Example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" becomes "eyJhbG...XVCJ9"
     *
     * <p>Tokens shorter than the minimum length are fully masked as "****".
     *
     * @param token the token to mask (may be null)
     * @return masked token safe for logging
     */
    public static String maskToken(String token) {
        if (token == null || token.length() < TOKEN_MIN_LENGTH_FOR_MASKING) {
            return "****";
        }
        return token.substring(0, TOKEN_PREFIX_LENGTH)
                + TOKEN_MASK
                + token.substring(token.length() - TOKEN_SUFFIX_LENGTH);
    }

    /**
     * Checks if a string is null or blank (empty or whitespace only).
     *
     * @param value the string to check
     * @return true if null or blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Checks if a string is not null and not blank.
     *
     * @param value the string to check
     * @return true if not null and contains non-whitespace characters
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
