package com.pim.backend.taxonomy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Taxonomy nodes and their direct attributes (spec FR-001, FR-005, FR-009; data-model.md). */
class TaxonomyNodeTest {

    private static final Instant CREATED = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-12T11:00:00Z");

    @Test
    void startsWithoutAttributesAndUpperCasesTheParent() {
        TaxonomyNode node = TaxonomyNode.create("CLS000002", NodeName.of("Camisas"), "  ", "cls000001", CREATED);

        assertThat(node.attributeIds()).isEmpty();
        assertThat(node.parentId()).contains("CLS000001");
        assertThat(node.description()).isEmpty();
        assertThat(node.updatedAt()).isEmpty();
        assertThat(node.reference()).isEqualTo(new NodeReference("CLS000002", "Camisas"));
    }

    @Test
    void appendsNewAttributesUpperCasedInOrderAndSkipsDirectOnes() {
        TaxonomyNode node = TaxonomyNode.create("CLS000001", NodeName.of("Ropa"), null, null, CREATED)
                .withAttributes(List.of("atr000003", "ATR000001"), LATER);

        TaxonomyNode again = node.withAttributes(List.of("ATR000001", "atr000002"), LATER);

        assertThat(node.attributeIds()).containsExactly("ATR000003", "ATR000001");
        assertThat(again.attributeIds()).containsExactly("ATR000003", "ATR000001", "ATR000002");
        assertThat(again.hasDirect("atr000002")).isTrue();
        assertThat(again.updatedAt()).contains(LATER);
    }

    @Test
    void removesADirectAttribute() {
        TaxonomyNode node = TaxonomyNode.create("CLS000001", NodeName.of("Ropa"), null, null, CREATED)
                .withAttributes(List.of("ATR000001", "ATR000002"), LATER);

        assertThat(node.withoutAttribute("atr000001", LATER).attributeIds()).containsExactly("ATR000002");
    }

    @Test
    void renamingKeepsIdentityParentAndAttributes() {
        TaxonomyNode node = TaxonomyNode.create("CLS000002", NodeName.of("Camisas"), null, "CLS000001", CREATED)
                .withAttributes(List.of("ATR000001"), CREATED);

        TaxonomyNode renamed = node.renamed(NodeName.of("Camisas de vestir"), " Formales ", LATER);

        assertThat(renamed.systemId()).isEqualTo("CLS000002");
        assertThat(renamed.parentId()).contains("CLS000001");
        assertThat(renamed.attributeIds()).containsExactly("ATR000001");
        assertThat(renamed.createdAt()).isEqualTo(CREATED);
        assertThat(renamed.updatedAt()).contains(LATER);
        assertThat(renamed.description()).contains("Formales");
    }

    @Test
    void refusesALongDescription() {
        assertThatThrownBy(() -> TaxonomyNode.create("CLS000001", NodeName.of("Ropa"), "x".repeat(501), null, CREATED))
                .isInstanceOfSatisfying(InvalidNodeException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.INVALID_NODE_DESCRIPTION);
                    assertThat(exception.field()).isEqualTo("description");
                });
        assertThat(TaxonomyNode.normalizedDescription("x".repeat(500))).isPresent();
    }
}
