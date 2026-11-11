package com.pim.backend.lov.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** List names: trimmed, 1–80 characters, compared ignoring letter case (spec FR-003; research R-06). */
class ListNameTest {

    @Test
    void trimsAndKeepsTheCaseGiven() {
        ListName name = ListName.of("  Color Base ");

        assertThat(name.value()).isEqualTo("Color Base");
        assertThat(name.comparisonKey()).isEqualTo("color base");
    }

    @Test
    void acceptsEightyCharactersAndRefusesEightyOne() {
        assertThat(ListName.of("n".repeat(80)).value()).hasSize(80);
        assertInvalid("n".repeat(81), LovErrorCode.INVALID_LIST_NAME);
    }

    @Test
    void refusesBlankAndMissingNames() {
        assertInvalid("   ", LovErrorCode.INVALID_LIST_NAME);
        assertInvalid(null, LovErrorCode.REQUIRED);
    }

    @Test
    void namesDifferingOnlyInCaseAreEqual() {
        assertThat(ListName.of("COLOR")).isEqualTo(ListName.of("color"));
    }

    private static void assertInvalid(String text, LovErrorCode code) {
        assertThatThrownBy(() -> ListName.of(text)).isInstanceOfSatisfying(InvalidListException.class, exception -> {
            assertThat(exception.field()).isEqualTo("name");
            assertThat(exception.code()).isEqualTo(code);
        });
    }
}
