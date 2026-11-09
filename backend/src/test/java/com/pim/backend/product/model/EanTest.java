package com.pim.backend.product.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EanTest {

    @ParameterizedTest
    @CsvSource({
        "7501234567890, 7501234567890",
        "'750 1234-567890', 7501234567890",
        "12345670, 12345670",
        "123456789012, 123456789012",
        "17501234567893, 17501234567893"
    })
    void acceptsValidEansAndRemovesSpacesAndDashes(String raw, String expected) {
        assertThat(Ean.parse(raw).value()).isEqualTo(expected);
    }

    @Test
    void doesNotValidateTheCheckDigit() {
        assertThat(Ean.parse("7501234567891").value()).isEqualTo("7501234567891");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingValues(String raw) {
        assertRejected(raw, EanErrorCode.REQUIRED);
    }

    @Test
    void rejectsMoreThanTwentyCharacters() {
        assertRejected("750123456789012345678", EanErrorCode.TOO_LONG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"licuadora", "750#123"})
    void rejectsCharactersOtherThanDigitsSpacesAndDashes(String raw) {
        assertRejected(raw, EanErrorCode.INVALID_CHARACTERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"750123456", "7501234567890123"})
    void rejectsLengthsOtherThan8_12_13Or14Digits(String raw) {
        assertRejected(raw, EanErrorCode.INVALID_LENGTH);
    }

    private static void assertRejected(String raw, EanErrorCode expectedCode) {
        assertThatThrownBy(() -> Ean.parse(raw))
                .isInstanceOfSatisfying(
                        InvalidEanException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expectedCode));
    }
}
