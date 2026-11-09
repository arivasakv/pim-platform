package com.pim.backend.identifier.model;

import java.util.regex.Pattern;

/**
 * The automatic ID sequence of one object type (FR-006; research R-05, plan decision D-2). A generated ID is the
 * prefix (alphanumeric sequences only) followed by the number, zero-padded to {@code minimumDigits}; the number
 * grows past the padding when needed.
 *
 * @param kind numeric or alphanumeric
 * @param prefix 1–10 capital letters for {@code ALPHANUMERIC}; {@code null} for {@code NUMERIC}
 * @param minimumDigits zero padding, 1–18
 * @param nextValue the next number to try, 1 to {@link #MAX_VALUE}
 */
public record SequenceConfiguration(SequenceKind kind, String prefix, int minimumDigits, long nextValue) {

    /** Largest number a sequence can produce (18 digits). */
    public static final long MAX_VALUE = 999_999_999_999_999_999L;

    private static final Pattern PREFIX = Pattern.compile("[A-Z]{1,10}");

    public SequenceConfiguration {
        if (kind == null) {
            throw new InvalidIdentifierRequestException("sequence.kind", IdentifierErrorCode.INVALID_SEQUENCE_KIND);
        }
        boolean validPrefix = kind == SequenceKind.ALPHANUMERIC
                ? prefix != null && PREFIX.matcher(prefix).matches()
                : prefix == null;
        if (!validPrefix) {
            throw new InvalidIdentifierRequestException("sequence.prefix", IdentifierErrorCode.INVALID_PREFIX);
        }
        if (minimumDigits < 1 || minimumDigits > 18) {
            throw new InvalidIdentifierRequestException(
                    "sequence.minimumDigits", IdentifierErrorCode.INVALID_MINIMUM_DIGITS);
        }
        if (nextValue < 1 || nextValue > MAX_VALUE) {
            throw new InvalidIdentifierRequestException("sequence.nextValue", IdentifierErrorCode.INVALID_NEXT_VALUE);
        }
    }

    /** The ID this sequence produces for {@code number}. */
    public String format(long number) {
        String digits = String.format("%0" + minimumDigits + "d", number);
        return prefix == null ? digits : prefix + digits;
    }

    /** A copy with another next value. */
    public SequenceConfiguration withNextValue(long value) {
        return new SequenceConfiguration(kind, prefix, minimumDigits, value);
    }
}
