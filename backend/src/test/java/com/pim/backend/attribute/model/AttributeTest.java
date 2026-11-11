package com.pim.backend.attribute.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The attribute definition (FR-003, FR-018; data-model "Attribute"). */
class AttributeTest {

    private static final Instant NOW = Instant.parse("2026-11-09T18:00:00Z");

    @Test
    void keepsItsValuesAndIsAlwaysGlobal() {
        Attribute attribute = Attribute.create(
                "ATR000001",
                DisplayName.of("Apto para regalo"),
                " Para envolver ",
                AttributeDataType.BOOLEAN,
                null,
                NOW);

        assertThat(attribute.systemId()).isEqualTo("ATR000001");
        assertThat(attribute.displayName().value()).isEqualTo("Apto para regalo");
        assertThat(attribute.description()).contains("Para envolver");
        assertThat(attribute.dataType()).isEqualTo(AttributeDataType.BOOLEAN);
        assertThat(attribute.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(attribute.properties()).isNull();
        assertThat(attribute.createdAt()).isEqualTo(NOW);
    }

    @Test
    void storesABlankDescriptionAsAbsent() {
        assertThat(create("   ").description()).isEmpty();
        assertThat(create(null).description()).isEmpty();
    }

    @Test
    void acceptsADescriptionOfUpToOneThousandCharacters() {
        assertThat(create("d".repeat(1000)).description())
                .hasValueSatisfying(text -> assertThat(text).hasSize(1000));
        assertThatThrownBy(() -> create("d".repeat(1001)))
                .isInstanceOfSatisfying(InvalidAttributeException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("description");
                    assertThat(exception.code()).isEqualTo(AttributeErrorCode.INVALID_DESCRIPTION);
                });
    }

    @Test
    void aBooleanHasNoProperties() {
        assertThatThrownBy(() -> Attribute.create(
                        "ATR1",
                        DisplayName.of("X"),
                        null,
                        AttributeDataType.BOOLEAN,
                        propertiesOf(AttributeDataType.TEXT),
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anotherTypeNeedsPropertiesOfThatType() {
        assertThatThrownBy(() -> Attribute.create("ATR1", DisplayName.of("X"), null, AttributeDataType.TEXT, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Attribute.create(
                        "ATR1",
                        DisplayName.of("X"),
                        null,
                        AttributeDataType.TEXT,
                        propertiesOf(AttributeDataType.NUMERIC),
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Attribute.create(
                                "ATR1",
                                DisplayName.of("X"),
                                null,
                                AttributeDataType.TEXT,
                                propertiesOf(AttributeDataType.TEXT),
                                NOW)
                        .properties()
                        .dataType())
                .isEqualTo(AttributeDataType.TEXT);
    }

    /** Simple valid properties of a given type (the property records are tested on their own). */
    private static AttributeProperties propertiesOf(AttributeDataType type) {
        return switch (type) {
            case TEXT -> new TextProperties(null, null, null, null, false);
            case NUMERIC -> new NumericProperties(NumberFormat.INTEGER, null, null, null);
            default -> throw new IllegalArgumentException("No example properties for " + type);
        };
    }

    private static Attribute create(String description) {
        return Attribute.create("ATR1", DisplayName.of("X"), description, AttributeDataType.BOOLEAN, null, NOW);
    }

    // --- Feature 007 ---

    @Test
    void anUpdatedAttributeKeepsItsIdTypeScopeAndCreationDate() {
        Attribute original = Attribute.create(
                "ATR1",
                DisplayName.of("Peso"),
                null,
                AttributeDataType.NUMERIC,
                new NumericProperties(NumberFormat.INTEGER, null, null, null),
                NOW);
        Instant later = NOW.plusSeconds(60);

        Attribute updated = original.updated(
                DisplayName.of("Peso neto"),
                " Sin empaque ",
                new NumericProperties(NumberFormat.DECIMAL, 2, null, null),
                later);

        assertThat(original.updatedAt()).isEmpty();
        assertThat(updated.systemId()).isEqualTo("ATR1");
        assertThat(updated.dataType()).isEqualTo(AttributeDataType.NUMERIC);
        assertThat(updated.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(updated.createdAt()).isEqualTo(NOW);
        assertThat(updated.updatedAt()).contains(later);
        assertThat(updated.displayName().value()).isEqualTo("Peso neto");
        assertThat(updated.description()).contains("Sin empaque");
        assertThatThrownBy(() -> original.updated(DisplayName.of("Peso"), null, null, later))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
