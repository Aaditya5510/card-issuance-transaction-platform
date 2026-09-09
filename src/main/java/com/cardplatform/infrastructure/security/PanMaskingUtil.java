package com.cardplatform.infrastructure.security;

/**
 * PCI-DSS compliant Primary Account Number (PAN) masking utility.
 * PCI-DSS requirement: Display at most the first 6 (BIN) and last 4 digits, masking the middle digits with asterisks.
 */
public final class PanMaskingUtil {

    private static final char MASK_CHAR = '*';
    private static final int MIN_PAN_LENGTH = 12;
    private static final int PREFIX_LENGTH = 6;
    private static final int SUFFIX_LENGTH = 4;

    private PanMaskingUtil() {
    }

    public static String maskPan(String rawPan) {
        if (rawPan == null) {
            return null;
        }

        String digitsOnly = rawPan.replaceAll("\\D", "");
        if (digitsOnly.length() < MIN_PAN_LENGTH) {
            return "************";
        }

        String prefix = digitsOnly.substring(0, PREFIX_LENGTH);
        String suffix = digitsOnly.substring(digitsOnly.length() - SUFFIX_LENGTH);
        int maskedLength = digitsOnly.length() - PREFIX_LENGTH - SUFFIX_LENGTH;

        return prefix + String.valueOf(MASK_CHAR).repeat(maskedLength) + suffix;
    }
}
