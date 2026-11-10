package com.pim.backend.attributegroup.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An attribute group (data-model.md "AttributeGroup"; FR-001, FR-004, FR-005, FR-008, FR-016). */
class AttributeGroupTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-09T10:15:30Z");
    private static final String ID = "3f1c2a54-0000-4000-8000-000000000001";

    @Test
    void startsWithoutAttributes() {
        AttributeGroup group = AttributeGroup.create(ID, GroupName.of("Logísticos"), " Dimensiones ", CREATED_AT);

        assertThat(group.id()).isEqualTo(ID);
        assertThat(group.name().value()).isEqualTo("Logísticos");
        assertThat(group.description()).contains("Dimensiones");
        assertThat(group.attributeIds()).isEmpty();
        assertThat(group.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void blankDescriptionIsAbsent() {
        assertThat(AttributeGroup.create(ID, GroupName.of("Logísticos"), "   ", CREATED_AT)
                        .description())
                .isEmpty();
        assertThat(AttributeGroup.create(ID, GroupName.of("Logísticos"), null, CREATED_AT)
                        .description())
                .isEmpty();
    }

    @Test
    void acceptsADescriptionOfFiveHundredCharacters() {
        String description = "d".repeat(500);

        assertThat(AttributeGroup.create(ID, GroupName.of("Logísticos"), description, CREATED_AT)
                        .description())
                .contains(description);
    }

    @Test
    void refusesADescriptionLongerThanFiveHundredCharacters() {
        assertThatThrownBy(() -> AttributeGroup.create(ID, GroupName.of("Logísticos"), "d".repeat(501), CREATED_AT))
                .isInstanceOfSatisfying(InvalidGroupException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("description");
                    assertThat(exception.code()).isEqualTo(AttributeGroupErrorCode.INVALID_GROUP_DESCRIPTION);
                });
    }

    @Test
    void copiesKeepTheIdentifier() {
        AttributeGroup group = AttributeGroup.create(ID, GroupName.of("Logisticos"), null, CREATED_AT);

        AttributeGroup withMembers = group.withAttributes(List.of("ATR000001", "ATR000002"));
        AttributeGroup renamed = withMembers.renamed(GroupName.of("Logísticos"), "Peso y volumen");

        assertThat(withMembers.id()).isEqualTo(ID);
        assertThat(withMembers.attributeIds()).containsExactly("ATR000001", "ATR000002");
        assertThat(renamed.id()).isEqualTo(ID);
        assertThat(renamed.name().value()).isEqualTo("Logísticos");
        assertThat(renamed.description()).contains("Peso y volumen");
        assertThat(renamed.attributeIds()).containsExactly("ATR000001", "ATR000002");
        assertThat(renamed.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void attributeIdsAreUnmodifiable() {
        AttributeGroup group = AttributeGroup.create(ID, GroupName.of("Logísticos"), null, CREATED_AT)
                .withAttributes(List.of("ATR000001"));

        assertThatThrownBy(() -> group.attributeIds().add("ATR000002"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anAttributeAppearsAtMostOnceIgnoringCase() {
        AttributeGroup group = AttributeGroup.create(ID, GroupName.of("Logísticos"), null, CREATED_AT);

        assertThatThrownBy(() -> group.withAttributes(List.of("ATR000001", "atr000001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsIgnoresCase() {
        AttributeGroup group = AttributeGroup.create(ID, GroupName.of("Logísticos"), null, CREATED_AT)
                .withAttributes(List.of("ATR000001"));

        assertThat(group.contains("atr000001")).isTrue();
        assertThat(group.contains("ATR000002")).isFalse();
    }
}
