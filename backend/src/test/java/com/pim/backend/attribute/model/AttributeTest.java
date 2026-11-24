package com.pim.backend.attribute.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The attribute definition (FR-003, FR-018; data-model "Attribute"). No description since 2026-11-20 (FR-003). */
class AttributeTest {

    private static final Instant NOW = Instant.parse("2026-11-09T18:00:00Z");

    @Test
    void keepsItsValuesAndIsAlwaysGlobal() {
        Attribute attribute =
                Attribute.create("ATR000001", DisplayName.of("Apto para regalo"), AttributeDataType.BOOLEAN, null, NOW);

        assertThat(attribute.systemId()).isEqualTo("ATR000001");
        assertThat(attribute.displayName().value()).isEqualTo("Apto para regalo");
        assertThat(attribute.dataType()).isEqualTo(AttributeDataType.BOOLEAN);
        assertThat(attribute.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(attribute.properties()).isNull();
        assertThat(attribute.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aBooleanHasNoProperties() {
        assertThatThrownBy(() -> Attribute.create(
                        "ATR1",
                        DisplayName.of("X"),
                        AttributeDataType.BOOLEAN,
                        propertiesOf(AttributeDataType.TEXT),
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anotherTypeNeedsPropertiesOfThatType() {
        assertThatThrownBy(() -> Attribute.create("ATR1", DisplayName.of("X"), AttributeDataType.TEXT, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Attribute.create(
                        "ATR1",
                        DisplayName.of("X"),
                        AttributeDataType.TEXT,
                        propertiesOf(AttributeDataType.NUMERIC),
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Attribute.create(
                                "ATR1",
                                DisplayName.of("X"),
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

    // --- Feature 007 ---

    @Test
    void anUpdatedAttributeKeepsItsIdTypeScopeAndCreationDate() {
        Attribute original = Attribute.create(
                "ATR1",
                DisplayName.of("Peso"),
                AttributeDataType.NUMERIC,
                new NumericProperties(NumberFormat.INTEGER, null, null, null),
                NOW);
        Instant later = NOW.plusSeconds(60);

        Attribute updated = original.updated(
                DisplayName.of("Peso neto"), new NumericProperties(NumberFormat.DECIMAL, 2, null, null), later);

        assertThat(original.updatedAt()).isEmpty();
        assertThat(updated.systemId()).isEqualTo("ATR1");
        assertThat(updated.dataType()).isEqualTo(AttributeDataType.NUMERIC);
        assertThat(updated.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(updated.createdAt()).isEqualTo(NOW);
        assertThat(updated.updatedAt()).contains(later);
        assertThat(updated.displayName().value()).isEqualTo("Peso neto");
        assertThatThrownBy(() -> original.updated(DisplayName.of("Peso"), null, later))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Feature 014 ---

    @Test
    void unlinkingGivesANewDataTypeAndKeepsEverythingElse() {
        Attribute talla = Attribute.create(
                "ATR1",
                DisplayName.of("Talla"),
                AttributeDataType.LIST_OF_VALUES,
                new ListOfValuesProperties(SelectionMode.SINGLE, "LOV1"),
                NOW);
        Instant later = NOW.plusSeconds(60);

        Attribute unlinked = talla.withDataType(
                AttributeDataType.NUMERIC, new NumericProperties(NumberFormat.DECIMAL, 1, null, null), later);

        assertThat(unlinked.systemId()).isEqualTo("ATR1");
        assertThat(unlinked.displayName().value()).isEqualTo("Talla");
        assertThat(unlinked.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(unlinked.createdAt()).isEqualTo(NOW);
        assertThat(unlinked.updatedAt()).contains(later);
        assertThat(unlinked.dataType()).isEqualTo(AttributeDataType.NUMERIC);
        assertThat(unlinked.properties()).isInstanceOf(NumericProperties.class);
        assertThat(talla.withDataType(AttributeDataType.BOOLEAN, null, later).properties())
                .isNull();
        assertThatThrownBy(() -> talla.withDataType(AttributeDataType.TEXT, null, later))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
