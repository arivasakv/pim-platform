package com.pim.backend.taxonomy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Allowed values of a list attribute at a node (feature 012 FR-007 to FR-011; data-model "Allowed values"). */
class AllowedValuesTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final List<ListValueOption> COLORES = List.of(
            new ListValueOption("0001", "Rojo"),
            new ListValueOption("0002", "Azul"),
            new ListValueOption("0003", "Verde"),
            new ListValueOption("0004", "Negro"),
            new ListValueOption("0005", "Blanco"),
            new ListValueOption("0006", "Amarillo"),
            new ListValueOption("0007", "Rosa"));

    private static final TaxonomyNode ROPA = node("CLS000001", "Ropa", null);
    private static final TaxonomyNode HOMBRE = node("CLS000002", "Ropa de hombre", "CLS000001");
    private static final TaxonomyNode CAMISAS = node("CLS000003", "Camisas", "CLS000002");
    private static final TaxonomyNode FORMALES = node("CLS000004", "Camisas formales", "CLS000003");

    private final Map<String, ValueFilter> filters = new HashMap<>();

    private static TaxonomyNode node(String id, String name, String parent) {
        return TaxonomyNode.create(id, NodeName.of(name), null, parent, NOW);
    }

    private void filter(TaxonomyNode node, String... ids) {
        filters.put(node.id(), ValueFilter.create(node.systemId(), "ATR000001", List.of(ids), NOW));
    }

    private static List<String> labels(List<ListValueOption> values) {
        return values.stream().map(ListValueOption::label).toList();
    }

    @Test
    void withoutFiltersEveryValueIsAllowedInListOrder() {
        AllowedValues values = AllowedValues.of(List.of(ROPA, HOMBRE), COLORES, filters);

        assertThat(labels(values.allowed())).hasSize(7).startsWith("Rojo", "Azul");
        assertThat(values.availableAbove()).isEqualTo(COLORES);
        assertThat(values.ownFilter()).isEmpty();
        assertThat(values.involved()).isEmpty();
        assertThat(values.noneAllowed()).isFalse();
    }

    @Test
    void aFilterAllowsOnlyItsValuesInListOrderAtTheNodeAndBelow() {
        filter(HOMBRE, "0005", "0002", "0004", "0001");

        AllowedValues atHombre = AllowedValues.of(List.of(ROPA, HOMBRE), COLORES, filters);
        AllowedValues atFormales = AllowedValues.of(List.of(ROPA, HOMBRE, CAMISAS, FORMALES), COLORES, filters);

        assertThat(labels(atHombre.allowed())).containsExactly("Rojo", "Azul", "Negro", "Blanco");
        assertThat(atHombre.availableAbove()).isEqualTo(COLORES);
        assertThat(labels(atHombre.includedValues())).containsExactly("Rojo", "Azul", "Negro", "Blanco");
        assertThat(atHombre.involved()).containsExactly(new AllowedValues.FilterOnPath(HOMBRE.reference(), 4));
        assertThat(labels(atFormales.allowed())).containsExactly("Rojo", "Azul", "Negro", "Blanco");
        assertThat(atFormales.ownFilter()).isEmpty();
    }

    @Test
    void filtersOnSeveralLevelsIntersect() {
        filter(HOMBRE, "0001", "0002", "0004", "0005");
        filter(CAMISAS, "0002", "0005");

        AllowedValues atCamisas = AllowedValues.of(List.of(ROPA, HOMBRE, CAMISAS), COLORES, filters);
        AllowedValues atFormales = AllowedValues.of(List.of(ROPA, HOMBRE, CAMISAS, FORMALES), COLORES, filters);

        assertThat(labels(atCamisas.allowed())).containsExactly("Azul", "Blanco");
        assertThat(labels(atCamisas.availableAbove())).containsExactly("Rojo", "Azul", "Negro", "Blanco");
        assertThat(atCamisas.availableAboveKeys()).contains("0004").doesNotContain("0003");
        assertThat(labels(atFormales.allowed())).containsExactly("Azul", "Blanco");
        assertThat(atFormales.involved()).extracting(f -> f.node().name()).containsExactly("Ropa de hombre", "Camisas");
    }

    @Test
    void ownValuesExcludedAboveAreReportedWithTheNearestExcludingAncestor() {
        filter(ROPA, "0002", "0004", "0005");
        filter(HOMBRE, "0002", "0004");
        filter(CAMISAS, "0002", "0005");

        AllowedValues atCamisas = AllowedValues.of(List.of(ROPA, HOMBRE, CAMISAS), COLORES, filters);

        assertThat(labels(atCamisas.allowed())).containsExactly("Azul");
        assertThat(atCamisas.excludedAbove())
                .containsExactly(
                        new AllowedValues.ExcludedValue(new ListValueOption("0005", "Blanco"), HOMBRE.reference()));
    }

    @Test
    void noValueAllowedWhenFiltersDoNotShareAValue() {
        filter(HOMBRE, "0004");
        filter(CAMISAS, "0002");

        AllowedValues atCamisas = AllowedValues.of(List.of(ROPA, HOMBRE, CAMISAS), COLORES, filters);

        assertThat(atCamisas.allowed()).isEmpty();
        assertThat(atCamisas.noneAllowed()).isTrue();
        assertThat(atCamisas.involved()).hasSize(2);
    }

    @Test
    void comparesIdsIgnoringCaseAndIgnoresIdsNoLongerInTheList() {
        List<ListValueOption> codes =
                List.of(new ListValueOption("ab", "Primero"), new ListValueOption("CD", "Segundo"));
        filter(HOMBRE, "AB", "cd", "gone");

        AllowedValues values = AllowedValues.of(List.of(ROPA, HOMBRE), codes, filters);

        assertThat(labels(values.allowed())).containsExactly("Primero", "Segundo");
        assertThat(values.involved()).containsExactly(new AllowedValues.FilterOnPath(HOMBRE.reference(), 2));
    }
}
