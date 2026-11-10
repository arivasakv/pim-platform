package com.pim.backend.attributegroup.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Group names: trimmed, 1–80 characters, unique ignoring letter case (FR-003; plan decision D-5). */
class GroupNameTest {

    @Test
    void isTrimmedAndKeepsItsCase() {
        GroupName name = GroupName.of("  Logísticos  ");

        assertThat(name.value()).isEqualTo("Logísticos");
        assertThat(name.comparisonKey()).isEqualTo("logísticos");
    }

    @Test
    void acceptsEightyCharacters() {
        assertThat(GroupName.of("a".repeat(80)).value()).hasSize(80);
    }

    @Test
    void isRequired() {
        assertThatThrownBy(() -> GroupName.of(null)).isInstanceOfSatisfying(InvalidGroupException.class, exception -> {
            assertThat(exception.field()).isEqualTo("name");
            assertThat(exception.code()).isEqualTo(AttributeGroupErrorCode.REQUIRED);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 81})
    void refusesBlankOrTooLongNames(int length) {
        String text = length == 0 ? "   " : "a".repeat(length);

        assertThatThrownBy(() -> GroupName.of(text)).isInstanceOfSatisfying(InvalidGroupException.class, exception -> {
            assertThat(exception.field()).isEqualTo("name");
            assertThat(exception.code()).isEqualTo(AttributeGroupErrorCode.INVALID_GROUP_NAME);
            assertThat(exception.getMessage()).isEqualTo("El nombre del grupo debe tener de 1 a 80 caracteres.");
        });
    }

    @Test
    void equalityIgnoresLetterCase() {
        assertThat(GroupName.of("Logísticos")).isEqualTo(GroupName.of(" LOGÍSTICOS "));
        assertThat(GroupName.of("Logísticos")).hasSameHashCodeAs(GroupName.of("logísticos"));
        assertThat(GroupName.of("Logísticos")).isNotEqualTo(GroupName.of("Logística"));
    }
}
