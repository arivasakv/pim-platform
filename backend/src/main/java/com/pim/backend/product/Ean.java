package com.pim.backend.product;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A valid, normalized EAN (data-model.md, "Ean"). Build it with {@link #parse(String)}; the same rule is
 * applied in the frontend for instant feedback, but this one is authoritative (constitution VIII).
 *
 * @param value the EAN digits, without spaces or dashes
 */
public record Ean(String value) {

    private static final int MAX_RAW_LENGTH = 20;
    private static final Set<Integer> VALID_LENGTHS = Set.of(8, 12, 13, 14);
    private static final Pattern ALLOWED_RAW_CHARACTERS = Pattern.compile("[0-9 -]*");
    private static final Pattern SEPARATORS = Pattern.compile("[ -]");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    public Ean {
        if (value == null || !DIGITS.matcher(value).matches() || !VALID_LENGTHS.contains(value.length())) {
            throw new InvalidEanException(EanErrorCode.INVALID_LENGTH);
        }
    }

    /**
     * Normalizes and validates user input, applying the rules in order: required, at most 20 characters,
     * only digits, spaces and dashes, then 8, 12, 13 or 14 digits once spaces and dashes are removed. The
     * check digit is not validated (spec A-004).
     *
     * @throws InvalidEanException with the first rule that fails
     */
    public static Ean parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidEanException(EanErrorCode.REQUIRED);
        }
        if (raw.length() > MAX_RAW_LENGTH) {
            throw new InvalidEanException(EanErrorCode.TOO_LONG);
        }
        if (!ALLOWED_RAW_CHARACTERS.matcher(raw).matches()) {
            throw new InvalidEanException(EanErrorCode.INVALID_CHARACTERS);
        }
        String digits = SEPARATORS.matcher(raw).replaceAll("");
        if (!VALID_LENGTHS.contains(digits.length())) {
            throw new InvalidEanException(EanErrorCode.INVALID_LENGTH);
        }
        return new Ean(digits);
    }
}
