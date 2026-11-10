package com.pim.backend.attribute.model;

import static com.pim.backend.attribute.model.TextPropertiesTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Numeric properties (FR-010; plan decision D-8). */
class NumericPropertiesTest {

    private static final String PRECISION = "numericProperties.precision";

    @Test
    void requiresTheNumberFormat() {
        assertRejected(
                () -> new NumericProperties(null, null, null, null),
                "numericProperties.numberFormat",
                AttributeErrorCode.REQUIRED);
    }

    @Test
    void aDecimalNeedsAPrecisionFromOneToTen() {
        assertThat(decimal(2, null, null).precision()).isEqualTo(2);
        assertThat(decimal(10, null, null).precision()).isEqualTo(10);
        assertRejected(() -> decimal(null, null, null), PRECISION, AttributeErrorCode.INVALID_PRECISION);
        assertRejected(() -> decimal(0, null, null), PRECISION, AttributeErrorCode.INVALID_PRECISION);
        assertRejected(() -> decimal(11, null, null), PRECISION, AttributeErrorCode.INVALID_PRECISION);
    }

    @Test
    void anIntegerHasNoPrecision() {
        assertThat(integer(null, null).precision()).isNull();
        assertRejected(
                () -> new NumericProperties(NumberFormat.INTEGER, 2, null, null),
                PRECISION,
                AttributeErrorCode.INVALID_PRECISION);
    }

    @Test
    void rangesMayBeOneSided() {
        assertThat(integer(new BigDecimal("0"), null).minimum()).isEqualByComparingTo("0");
        assertThat(integer(null, new BigDecimal("100")).maximum()).isEqualByComparingTo("100");
    }

    @Test
    void valuesFitTheFormat() {
        assertRejected(
                () -> integer(new BigDecimal("1.5"), null),
                "numericProperties.minimum",
                AttributeErrorCode.INVALID_NUMBER);
        assertRejected(
                () -> decimal(2, null, new BigDecimal("1.234")),
                "numericProperties.maximum",
                AttributeErrorCode.INVALID_NUMBER);
        assertRejected(
                () -> integer(new BigDecimal("1234567890123456789"), null),
                "numericProperties.minimum",
                AttributeErrorCode.INVALID_NUMBER);
        assertThat(integer(new BigDecimal("2.000"), null).minimum()).isEqualByComparingTo("2");
        assertThat(decimal(2, new BigDecimal("0.50"), new BigDecimal("999.99")).maximum())
                .isEqualByComparingTo("999.99");
    }

    @Test
    void minimumMayNotExceedMaximum() {
        assertRejected(
                () -> integer(new BigDecimal("5"), new BigDecimal("1")),
                "numericProperties.maximum",
                AttributeErrorCode.INVALID_VALUE_RANGE);
    }

    private static NumericProperties decimal(Integer precision, BigDecimal minimum, BigDecimal maximum) {
        return new NumericProperties(NumberFormat.DECIMAL, precision, minimum, maximum);
    }

    private static NumericProperties integer(BigDecimal minimum, BigDecimal maximum) {
        return new NumericProperties(NumberFormat.INTEGER, null, minimum, maximum);
    }
}
