package com.pim.backend.identifier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Sequence settings and ID format (FR-006, research R-05, data-model "SequenceConfiguration"). */
class SequenceConfigurationTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "prd", "PR1", "ABCDEFGHIJK"})
    void alphanumericNeedsAPrefixOfOneToTenCapitalLetters(String prefix) {
        assertRejected(
                () -> new SequenceConfiguration(SequenceKind.ALPHANUMERIC, prefix, 6, 1),
                IdentifierErrorCode.INVALID_PREFIX);
    }

    @Test
    void numericTakesNoPrefix() {
        assertRejected(
                () -> new SequenceConfiguration(SequenceKind.NUMERIC, "PRD", 6, 1), IdentifierErrorCode.INVALID_PREFIX);
    }

    @Test
    void requiresAKind() {
        assertRejected(() -> new SequenceConfiguration(null, null, 6, 1), IdentifierErrorCode.INVALID_SEQUENCE_KIND);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 19})
    void minimumDigitsIsOneToEighteen(int minimumDigits) {
        assertRejected(
                () -> new SequenceConfiguration(SequenceKind.NUMERIC, null, minimumDigits, 1),
                IdentifierErrorCode.INVALID_MINIMUM_DIGITS);
    }

    @Test
    void nextValueIsOneToTheMaximum() {
        assertRejected(
                () -> new SequenceConfiguration(SequenceKind.NUMERIC, null, 6, 0),
                IdentifierErrorCode.INVALID_NEXT_VALUE);
        assertRejected(
                () -> new SequenceConfiguration(SequenceKind.NUMERIC, null, 6, SequenceConfiguration.MAX_VALUE + 1),
                IdentifierErrorCode.INVALID_NEXT_VALUE);
        assertThat(new SequenceConfiguration(SequenceKind.NUMERIC, null, 18, SequenceConfiguration.MAX_VALUE)
                        .nextValue())
                .isEqualTo(999_999_999_999_999_999L);
    }

    @Test
    void formatsThePrefixAndTheZeroPaddedNumber() {
        SequenceConfiguration products = new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "PRD", 6, 1);

        assertThat(products.format(1)).isEqualTo("PRD000001");
        assertThat(products.format(1_234_567)).isEqualTo("PRD1234567");
    }

    @Test
    void formatsANumericSequenceWithoutPrefix() {
        assertThat(new SequenceConfiguration(SequenceKind.NUMERIC, null, 8, 1000).format(1000))
                .isEqualTo("00001000");
    }

    @Test
    void withNextValueReturnsACopy() {
        SequenceConfiguration original = new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "ATR", 6, 1);

        SequenceConfiguration copy = original.withNextValue(5);

        assertThat(copy).isEqualTo(new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "ATR", 6, 5));
        assertThat(original.nextValue()).isEqualTo(1);
    }

    private static void assertRejected(Runnable action, IdentifierErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        InvalidIdentifierRequestException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
