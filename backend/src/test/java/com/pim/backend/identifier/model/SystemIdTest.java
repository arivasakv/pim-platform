package com.pim.backend.identifier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** System ID format and comparison (FR-001, research R-07, spec A-004). */
class SystemIdTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void treatsABlankManualIdAsNoIdGiven(String text) {
        assertThat(SystemId.parseManual(text)).isEmpty();
    }

    @Test
    void trimsSurroundingSpaces() {
        assertThat(SystemId.parseManual(" A-100 ")).map(SystemId::value).contains("A-100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "a-1", "CLS_ROPA.2026", "0123456789", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmn"})
    void acceptsOneToFortyLettersDigitsDashesUnderscoresAndDots(String text) {
        assertThat(SystemId.parseManual(text)).map(SystemId::value).contains(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A B", "A/1", "Ñ1", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno"})
    void rejectsOtherCharactersAndMoreThanFortyCharacters(String text) {
        assertThatThrownBy(() -> SystemId.parseManual(text))
                .isInstanceOfSatisfying(InvalidIdentifierRequestException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("systemId");
                    assertThat(exception.code()).isEqualTo(IdentifierErrorCode.INVALID_SYSTEM_ID);
                });
    }

    @Test
    void keepsTheCaseGivenButComparesIgnoringCase() {
        SystemId lower = SystemId.of("a-100");
        SystemId upper = SystemId.of("A-100");

        assertThat(lower.value()).isEqualTo("a-100");
        assertThat(lower).isEqualTo(upper).hasSameHashCodeAs(upper);
        assertThat(lower).isNotEqualTo(SystemId.of("A-101"));
    }
}
