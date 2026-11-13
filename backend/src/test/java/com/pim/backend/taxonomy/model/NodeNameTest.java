package com.pim.backend.taxonomy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Node names: trimmed, 1–80 characters, compared ignoring letter case (spec FR-004, A-006). */
class NodeNameTest {

    @Test
    void trimsAndKeepsTheCaseGiven() {
        NodeName name = NodeName.of("  Ropa de Hombre ");

        assertThat(name.value()).isEqualTo("Ropa de Hombre");
        assertThat(name.comparisonKey()).isEqualTo("ropa de hombre");
        assertThat(name).isEqualTo(NodeName.of("ROPA DE HOMBRE"));
    }

    @Test
    void acceptsEightyCharactersAndRefusesMore() {
        assertThat(NodeName.of("a".repeat(80)).value()).hasSize(80);
        assertThatThrownBy(() -> NodeName.of("a".repeat(81)))
                .isInstanceOfSatisfying(InvalidNodeException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.INVALID_NODE_NAME);
                    assertThat(exception.field()).isEqualTo("name");
                    assertThat(exception.getMessage()).isEqualTo("El nombre del nodo debe tener de 1 a 80 caracteres.");
                });
    }

    @Test
    void refusesBlankAndAbsentNames() {
        assertThatThrownBy(() -> NodeName.of("   "))
                .isInstanceOfSatisfying(
                        InvalidNodeException.class,
                        exception -> assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.INVALID_NODE_NAME));
        assertThatThrownBy(() -> NodeName.of(null))
                .isInstanceOfSatisfying(
                        InvalidNodeException.class,
                        exception -> assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.REQUIRED));
    }
}
