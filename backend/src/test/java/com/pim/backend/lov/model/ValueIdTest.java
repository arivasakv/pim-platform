package com.pim.backend.lov.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Value IDs: 1–40 characters of A–Z a–z 0–9 - _ ., kept as text, compared ignoring case (FR-007, R-07). */
class ValueIdTest {

    @Test
    void keepsLeadingZerosAndCase() {
        assertThat(ValueId.of("0001").value()).isEqualTo("0001");
        assertThat(ValueId.of("  Ab-1_x.2 ").value()).isEqualTo("Ab-1_x.2");
    }

    @Test
    void idsDifferingOnlyInCaseHaveTheSameKey() {
        assertThat(ValueId.of("abc").key()).isEqualTo("ABC");
        assertThat(ValueId.of("abc")).isEqualTo(ValueId.of("ABC"));
        assertThat(ValueId.of("0001")).isNotEqualTo(ValueId.of("1"));
    }

    @Test
    void acceptsFortyCharacters() {
        assertThat(ValueId.of("x".repeat(40)).value()).hasSize(40);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "a b", "ñ", "a/b", "é1", "x,y"})
    void refusesInvalidIds(String text) {
        assertThatThrownBy(() -> ValueId.of(text)).isInstanceOfSatisfying(InvalidListException.class, exception -> {
            assertThat(exception.field()).isEqualTo("id");
            assertThat(exception.code()).isEqualTo(LovErrorCode.INVALID_VALUE_ID);
        });
    }

    @Test
    void refusesFortyOneCharacters() {
        assertThatThrownBy(() -> ValueId.of("x".repeat(41))).isInstanceOf(InvalidListException.class);
    }
}
