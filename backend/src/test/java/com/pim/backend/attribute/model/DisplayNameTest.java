package com.pim.backend.attribute.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Display name of an attribute (FR-003, FR-023; plan decision D-7). */
class DisplayNameTest {

    @Test
    void requiresAName() {
        assertRejected(null, AttributeErrorCode.REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankName(String text) {
        assertRejected(text, AttributeErrorCode.INVALID_DISPLAY_NAME);
    }

    @Test
    void acceptsOneToOneHundredTwentyCharacters() {
        assertThat(DisplayName.of("A").value()).isEqualTo("A");
        assertThat(DisplayName.of("x".repeat(120)).value()).hasSize(120);
        assertRejected("x".repeat(121), AttributeErrorCode.INVALID_DISPLAY_NAME);
    }

    @Test
    void trimsSurroundingSpaces() {
        assertThat(DisplayName.of(" Color ").value()).isEqualTo("Color");
    }

    @Test
    void comparesIgnoringLetterCaseButKeepsTheCaseGiven() {
        DisplayName color = DisplayName.of("Color");

        assertThat(color).isEqualTo(DisplayName.of(" color ")).isEqualTo(DisplayName.of("COLOR"));
        assertThat(color).hasSameHashCodeAs(DisplayName.of("COLOR"));
        assertThat(color).isNotEqualTo(DisplayName.of("Colores"));
        assertThat(DisplayName.of("COLOR").value()).isEqualTo("COLOR");
    }

    private static void assertRejected(String text, AttributeErrorCode code) {
        assertThatThrownBy(() -> DisplayName.of(text))
                .isInstanceOfSatisfying(InvalidAttributeException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("displayName");
                    assertThat(exception.code()).isEqualTo(code);
                });
    }
}
