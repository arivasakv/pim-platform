package com.pim.backend.attribute.model;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Properties of a text attribute (ERF 1.1 §3.2 ¶2, §3.2.1 Texto; FR-008, FR-009; plan decisions D-6, D-7). All are
 * optional. The case format and the capture mask are separate properties, and both are allowed on rich text.
 *
 * @param minLength 0–100,000
 * @param maxLength 1–100,000, at least {@code minLength}
 * @param caseFormat case mask
 * @param captureMask regular expression of 1–500 characters that compiles
 * @param richText whether values are rich text (HTML)
 */
public record TextProperties(
        Integer minLength, Integer maxLength, CaseFormat caseFormat, String captureMask, boolean richText)
        implements AttributeProperties {

    /** Longest accepted length limit. */
    public static final int MAX_LENGTH_LIMIT = 100_000;

    /** Longest accepted capture mask. */
    public static final int MAX_CAPTURE_MASK_LENGTH = 500;

    public TextProperties {
        checkLength("textProperties.minLength", minLength, 0);
        checkLength("textProperties.maxLength", maxLength, 1);
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw new InvalidAttributeException("textProperties.maxLength", AttributeErrorCode.INVALID_LENGTH_RANGE);
        }
        if (captureMask != null && !isValidCaptureMask(captureMask)) {
            throw new InvalidAttributeException("textProperties.captureMask", AttributeErrorCode.INVALID_CAPTURE_MASK);
        }
    }

    @Override
    public AttributeDataType dataType() {
        return AttributeDataType.TEXT;
    }

    private static void checkLength(String field, Integer value, int lowest) {
        if (value != null && (value < lowest || value > MAX_LENGTH_LIMIT)) {
            throw new InvalidAttributeException(field, AttributeErrorCode.INVALID_LENGTH, lowest);
        }
    }

    private static boolean isValidCaptureMask(String mask) {
        if (mask.isEmpty() || mask.length() > MAX_CAPTURE_MASK_LENGTH) {
            return false;
        }
        try {
            Pattern.compile(mask);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }
}
