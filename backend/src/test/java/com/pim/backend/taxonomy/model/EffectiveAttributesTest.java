package com.pim.backend.taxonomy.model;

import static com.pim.backend.taxonomy.model.AssignmentType.DIRECT;
import static com.pim.backend.taxonomy.model.AssignmentType.INHERITED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Inheritance rules (spec FR-012 to FR-016; data-model.md), with the feature request's example: Ropa (Color,
 * Material, Marca) → Ropa de hombre (Género) → Camisas (Largo de manga) and Pantalones (Talla de cintura).
 */
class EffectiveAttributesTest {

    private static final Instant AT = Instant.parse("2026-11-12T10:00:00Z");
    private static final String COLOR = "ATR000001";
    private static final String MATERIAL = "ATR000002";
    private static final String MARCA = "ATR000003";
    private static final String GENERO = "ATR000004";
    private static final String MANGA = "ATR000005";
    private static final String CINTURA = "ATR000006";

    private final Map<String, TaxonomyNode> nodes = new HashMap<>();
    private final TaxonomyNode ropa = node("CLS000001", "Ropa", null, COLOR, MATERIAL, MARCA);
    private final TaxonomyNode hombre = node("CLS000002", "Ropa de hombre", ropa, GENERO);
    private final TaxonomyNode camisas = node("CLS000003", "Camisas", hombre, MANGA);
    private final TaxonomyNode pantalones = node("CLS000004", "Pantalones", hombre, CINTURA);

    private TaxonomyNode node(String id, String name, TaxonomyNode parent, String... attributes) {
        TaxonomyNode node = TaxonomyNode.create(
                        id, NodeName.of(name), null, parent == null ? null : parent.systemId(), AT)
                .withAttributes(List.of(attributes), AT);
        nodes.put(node.id(), node);
        return node;
    }

    private static EffectiveAttribute inherited(String attributeId, TaxonomyNode origin) {
        return new EffectiveAttribute(attributeId, INHERITED, origin.reference(), Optional.empty());
    }

    private static EffectiveAttribute direct(String attributeId, TaxonomyNode node) {
        return new EffectiveAttribute(attributeId, DIRECT, node.reference(), Optional.empty());
    }

    @Test
    void aLevelThreeNodeInheritsFromEveryAncestorInOrder() {
        assertThat(EffectiveAttributes.of(camisas, nodes))
                .containsExactly(
                        inherited(COLOR, ropa),
                        inherited(MATERIAL, ropa),
                        inherited(MARCA, ropa),
                        inherited(GENERO, hombre),
                        direct(MANGA, camisas));
    }

    @Test
    void siblingsDoNotShareTheirDirectAttributes() {
        assertThat(EffectiveAttributes.of(pantalones, nodes))
                .containsExactly(
                        inherited(COLOR, ropa),
                        inherited(MATERIAL, ropa),
                        inherited(MARCA, ropa),
                        inherited(GENERO, hombre),
                        direct(CINTURA, pantalones));
    }

    @Test
    void aTopLevelNodeHasOnlyDirectAttributes() {
        assertThat(EffectiveAttributes.of(ropa, nodes))
                .containsExactly(direct(COLOR, ropa), direct(MATERIAL, ropa), direct(MARCA, ropa));
    }

    @Test
    void aNodeWithoutDirectAttributesInheritsEverything() {
        TaxonomyNode formales = node("CLS000005", "Camisas formales", camisas);

        assertThat(EffectiveAttributes.of(formales, nodes))
                .extracting(EffectiveAttribute::attributeId, EffectiveAttribute::assignment)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(COLOR, INHERITED),
                        org.assertj.core.groups.Tuple.tuple(MATERIAL, INHERITED),
                        org.assertj.core.groups.Tuple.tuple(MARCA, INHERITED),
                        org.assertj.core.groups.Tuple.tuple(GENERO, INHERITED),
                        org.assertj.core.groups.Tuple.tuple(MANGA, INHERITED));
    }

    @Test
    void anAttributeOnTwoAncestorsAppearsOnceFromTheNearest() {
        TaxonomyNode hombreConColor = hombre.withAttributes(List.of(COLOR), AT);
        nodes.put(hombreConColor.id(), hombreConColor);

        List<EffectiveAttribute> effective = EffectiveAttributes.of(camisas, nodes);

        assertThat(effective)
                .filteredOn(entry -> entry.attributeId().equals(COLOR))
                .containsExactly(inherited(COLOR, hombreConColor));
        assertThat(effective).hasSize(5);
    }

    @Test
    void aDirectAttributeAlsoOnAnAncestorAppearsOnceAsDirectWithTheAncestor() {
        TaxonomyNode camisasConGenero = camisas.withAttributes(List.of(GENERO), AT);
        nodes.put(camisasConGenero.id(), camisasConGenero);

        List<EffectiveAttribute> effective = EffectiveAttributes.of(camisasConGenero, nodes);

        assertThat(effective).hasSize(5);
        assertThat(effective.subList(3, 5))
                .containsExactly(
                        direct(MANGA, camisasConGenero),
                        new EffectiveAttribute(
                                GENERO, DIRECT, camisasConGenero.reference(), Optional.of(hombre.reference())));
    }

    @Test
    void theChainGivesLevelAndPath() {
        assertThat(EffectiveAttributes.chain(camisas, nodes)).containsExactly(ropa, hombre, camisas);
        assertThat(EffectiveAttributes.chain(ropa, nodes)).containsExactly(ropa);
    }

    @Test
    void aMissingParentEndsTheChainWithoutFailing() {
        nodes.remove(ropa.id());

        assertThat(EffectiveAttributes.chain(camisas, nodes)).containsExactly(hombre, camisas);
        assertThat(EffectiveAttributes.of(camisas, nodes)).hasSize(2);
    }
}
