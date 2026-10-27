package ar.com.biopus.abismocam;

import java.math.BigInteger;

final class SourceIdentity {
    static String identifier(String value) {
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]{0,24}"))
            throw new IllegalArgumentException("Usá hasta 24 letras sin acentos, números, guiones o guiones bajos para identificar el teléfono.");
        return trimmed;
    }

    static BigInteger counter(String value) {
        try {
            BigInteger number = new BigInteger(value);
            return number.signum() > 0 ? number : BigInteger.ONE;
        } catch (NumberFormatException invalid) { return BigInteger.ONE; }
    }

    static String name(String identifier, BigInteger counter) {
        String digits = counter.toString();
        while (digits.length() < 4) digits = "0" + digits;
        return "abismoCam_" + (identifier.isEmpty() ? "" : identifier + "_") + digits;
    }
}
