package com.pim.backend.attribute.model;

import java.math.BigDecimal;

/**
 * Properties of a numeric attribute (ERF 1.1 §3.2.1 Numéricos, §3.2 ¶2; FR-010; plan decision D-8).
 *
 * @param numberFormat integer or decimal; required
 * @param precision decimals allowed: required and 1–10 for decimals, absent for integers
 * @param minimum optional lowest value
 * @param maximum optional highest value, at least {@code minimum}
 */
public record NumericProperties(NumberFormat numberFormat, Integer precision, BigDecimal minimum, BigDecimal maximum)
        implements AttributeProperties {

    /** Most significant digits a limit may have. */
    public static final int MAX_SIGNIFICANT_DIGITS = 18;

    public NumericProperties {
        if (numberFormat == null) {
            throw new InvalidAttributeException("numericProperties.numberFormat", AttributeErrorCode.REQUIRED);
        }
        boolean validPrecision = numberFormat == NumberFormat.DECIMAL
                ? precision != null && precision >= 1 && precision <= 10
                : precision == null;
        if (!validPrecision) {
            throw new InvalidAttributeException("numericProperties.precision", AttributeErrorCode.INVALID_PRECISION);
        }
        checkValue("numericProperties.minimum", minimum, numberFormat, precision);
        checkValue("numericProperties.maximum", maximum, numberFormat, precision);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new InvalidAttributeException("numericProperties.maximum", AttributeErrorCode.INVALID_VALUE_RANGE);
        }
    }

    @Override
    public AttributeDataType dataType() {
        return AttributeDataType.NUMERIC;
    }

    private static void checkValue(String field, BigDecimal value, NumberFormat format, Integer precision) {
        if (value == null) {
            return;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int decimals = Math.max(normalized.scale(), 0);
        int allowedDecimals = format == NumberFormat.INTEGER ? 0 : precision;
        if (normalized.precision() > MAX_SIGNIFICANT_DIGITS || decimals > allowedDecimals) {
            throw new InvalidAttributeException(field, AttributeErrorCode.INVALID_NUMBER);
        }
    }
}
