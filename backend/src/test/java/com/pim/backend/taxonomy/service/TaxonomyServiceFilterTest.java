package com.pim.backend.taxonomy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.ValueId;
import com.pim.backend.lov.repository.InMemoryListOfValuesRepository;
import com.pim.backend.lov.service.ListOfValuesCatalogAdapter;
import com.pim.backend.lov.service.ListOfValuesLookup;
import com.pim.backend.taxonomy.model.AllowedValues;
import com.pim.backend.taxonomy.model.AttributeNotListException;
import com.pim.backend.taxonomy.model.AttributeNotOnNodeException;
import com.pim.backend.taxonomy.model.FilterAttributeNotFoundException;
import com.pim.backend.taxonomy.model.FilterNotFoundException;
import com.pim.backend.taxonomy.model.InvalidFilterException;
import com.pim.backend.taxonomy.model.ListAttributeSummary;
import com.pim.backend.taxonomy.model.ListValueOption;
import com.pim.backend.taxonomy.model.NodeNotFoundException;
import com.pim.backend.taxonomy.model.TaxonomyChangeNotPermittedException;
import com.pim.backend.taxonomy.model.ValueExcludedAboveException;
import com.pim.backend.taxonomy.model.ValueFilterDetails;
import com.pim.backend.taxonomy.model.ValueNotInListException;
import com.pim.backend.taxonomy.repository.InMemoryTaxonomyNodeRepository;
import com.pim.backend.taxonomy.repository.InMemoryValueFilterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * List-value filters of {@link TaxonomyService} (feature 012 FR-001 to FR-013, plan D-6), on the quickstart example,
 * with the real attribute, identifier and lov lookup services on in-memory fakes.
 *
 * <pre>
 * List «Colores»: Rojo 0001, Azul 0002, Verde 0003, Negro 0004, Blanco 0005, Amarillo 0006, Rosa 0007
 * Ropa (Color) › Ropa de hombre › Camisas (Largo de manga) › Camisas formales; Ropa de hombre › Pantalones;
 * Ropa › Ropa de mujer; Calzado (Color)
 * </pre>
 */
class TaxonomyServiceFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-19T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);
    private static final String[][] COLORES = {
        {"0001", "Rojo"},
        {"0002", "Azul"},
        {"0003", "Verde"},
        {"0004", "Negro"},
        {"0005", "Blanco"},
        {"0006", "Amarillo"},
        {"0007", "Rosa"}
    };

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryTaxonomyNodeRepository nodes = new InMemoryTaxonomyNodeRepository();
    private final InMemoryValueFilterRepository filters = new InMemoryValueFilterRepository();
    private final InMemoryListOfValuesRepository lists = new InMemoryListOfValuesRepository();
    private final AttributeService attributeService;
    private final TaxonomyService service;
    private final Map<String, String> ids = new HashMap<>();
    private final Attribute color;
    private final Attribute largo;

    {
        lists.insert(new ListOfValues(
                "LOV000001",
                ListName.of("Colores"),
                false,
                java.util.Arrays.stream(COLORES)
                        .map(pair -> new ListValue(ValueId.of(pair[0]), pair[1], Optional.empty()))
                        .toList(),
                8,
                NOW,
                NOW));
        ListOfValuesLookup lookup = new ListOfValuesLookup(lists);
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        IdentifierService identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        attributeService = new AttributeService(
                new InMemoryAttributeRepository(),
                new ListOfValuesCatalogAdapter(lookup),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                clock);
        service = new TaxonomyService(nodes, filters, attributeService, lookup, identifierService, clock);
        color = attributeService.create(
                new AttributeDraft(
                        null,
                        "Color",
                        null,
                        AttributeDataType.LIST_OF_VALUES,
                        new ListOfValuesProperties(SelectionMode.SINGLE, "LOV000001")),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
        largo = attributeService.create(
                new AttributeDraft(null, "Largo de manga", null, AttributeDataType.BOOLEAN, null),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
        node("Ropa", null, color);
        node("Ropa de hombre", "Ropa");
        node("Camisas", "Ropa de hombre", largo);
        node("Camisas formales", "Camisas");
        node("Pantalones", "Ropa de hombre");
        node("Ropa de mujer", "Ropa");
        node("Calzado", null, color);
    }

    private void node(String name, String parent, Attribute... attributes) {
        String id = service.create(name, null, parent == null ? null : ids.get(parent), CAN_CHANGE)
                .node()
                .systemId();
        ids.put(name, id);
        if (attributes.length > 0) {
            service.addAttributes(
                    id,
                    java.util.Arrays.stream(attributes).map(Attribute::systemId).toList(),
                    CAN_CHANGE);
        }
    }

    private String id(String name) {
        return ids.get(name);
    }

    private ValueFilterDetails save(String node, String... valueIds) {
        return service.saveFilter(id(node), color.systemId(), List.of(valueIds), CAN_CHANGE);
    }

    private ValueFilterDetails find(String node) {
        return service.findFilter(id(node), color.systemId());
    }

    private static List<String> labels(List<ListValueOption> values) {
        return values.stream().map(ListValueOption::label).toList();
    }

    private ListAttributeSummary colorAt(String node) {
        return service.find(id(node)).listAttributes().stream()
                .filter(summary -> summary.displayName().equals("Color"))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> origins(ListAttributeSummary summary) {
        return summary.filters().stream()
                .map(filter -> filter.node().name() + ":" + filter.includedCount())
                .toList();
    }

    // --- User Story 1 ---

    @Test
    void savesAFilterAndReturnsTheAllowedValuesInListOrder() {
        ValueFilterDetails details = save("Ropa de hombre", "0002", "0004", "0005", "0001");

        assertThat(labels(details.values().allowed())).containsExactly("Rojo", "Azul", "Negro", "Blanco");
        assertThat(labels(details.values().includedValues())).containsExactly("Rojo", "Azul", "Negro", "Blanco");
        assertThat(details.attribute().allowedCount()).isEqualTo(4);
        assertThat(details.attribute().totalValues()).isEqualTo(7);
        assertThat(origins(details.attribute())).containsExactly("Ropa de hombre:4");
        assertThat(filters.find(id("Ropa de hombre"), color.systemId())
                        .orElseThrow()
                        .valueIds())
                .containsExactly("0001", "0002", "0004", "0005");
    }

    @Test
    void savingAgainReplacesTheFilterAndKeepsItsCreationDate() {
        save("Ropa de hombre", "0002", "0004");
        ValueFilterDetails details = save("Ropa de hombre", "0005");

        assertThat(labels(details.values().allowed())).containsExactly("Blanco");
        assertThat(filters.all()).hasSize(1);
        assertThat(filters.all().getFirst().updatedAt()).contains(NOW);
    }

    @Test
    void withoutAFilterEveryValueIsAvailable() {
        ValueFilterDetails details = find("Ropa de hombre");

        assertThat(details.values().allowed()).hasSize(7);
        assertThat(details.values().availableAbove()).hasSize(7);
        assertThat(details.values().ownFilter()).isEmpty();
        assertThat(details.attribute().listName()).isEqualTo("Colores");
        assertThat(details.attribute().selectionMode()).isEqualTo("SINGLE");
    }

    @Test
    void refusesInvalidRequestsInDataModelOrderAndStoresNothing() {
        assertThatThrownBy(() ->
                        service.saveFilter(id("Ropa de hombre"), color.systemId(), List.of("0001"), NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);
        assertThatThrownBy(() -> save("Ropa de hombre")).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> service.saveFilter("CLS999999", color.systemId(), List.of("0001"), CAN_CHANGE))
                .isInstanceOfSatisfying(
                        NodeNotFoundException.class, e -> assertThat(e.field()).isEqualTo("nodeId"));
        assertThatThrownBy(() -> service.saveFilter(id("Ropa de hombre"), "ATR999999", List.of("0001"), CAN_CHANGE))
                .isInstanceOf(FilterAttributeNotFoundException.class)
                .hasMessage("No existe un atributo con el ID ATR999999.");
        String suelto = service.create("Suelto", null, null, CAN_CHANGE).node().systemId();
        assertThatThrownBy(() -> service.saveFilter(suelto, color.systemId(), List.of("0001"), CAN_CHANGE))
                .isInstanceOf(AttributeNotOnNodeException.class);
        assertThatThrownBy(() -> service.saveFilter(id("Camisas"), largo.systemId(), List.of("0001"), CAN_CHANGE))
                .isInstanceOf(AttributeNotListException.class)
                .hasMessage(
                        "El atributo «Largo de manga» no es de tipo lista de valores; solo esos atributos se filtran.");
        assertThatThrownBy(() -> save("Ropa de hombre", "0001", "9999"))
                .isInstanceOf(ValueNotInListException.class)
                .hasMessage("La lista «Colores» no tiene un valor con el ID 9999.");
        assertThat(filters.all()).isEmpty();
    }

    @Test
    void theNodeDetailListsItsListAttributes() {
        ListAttributeSummary before = colorAt("Ropa de hombre");
        save("Ropa de hombre", "0001", "0002", "0004", "0005");
        ListAttributeSummary after = colorAt("Ropa de hombre");

        assertThat(before.allowedCount()).isEqualTo(7);
        assertThat(before.hasOwnFilter()).isFalse();
        assertThat(before.filters()).isEmpty();
        assertThat(after.allowedCount()).isEqualTo(4);
        assertThat(after.hasOwnFilter()).isTrue();
        assertThat(service.find(id("Camisas")).listAttributes())
                .extracting(ListAttributeSummary::displayName)
                .containsExactly("Color");
    }

    // --- User Story 2 ---

    @Test
    void filtersAreInheritedAndOnlyNarrowedBelow() {
        save("Ropa de hombre", "0001", "0002", "0004", "0005");

        for (String below : List.of("Camisas", "Camisas formales", "Pantalones")) {
            assertThat(colorAt(below).allowedCount()).as(below).isEqualTo(4);
            assertThat(colorAt(below).hasOwnFilter()).isFalse();
            assertThat(origins(colorAt(below))).containsExactly("Ropa de hombre:4");
        }
        assertThat(colorAt("Ropa de mujer").allowedCount()).isEqualTo(7);
        assertThat(colorAt("Ropa").allowedCount()).isEqualTo(7);
        assertThat(labels(find("Camisas").values().availableAbove()))
                .containsExactly("Rojo", "Azul", "Negro", "Blanco");

        save("Camisas", "0002", "0005");

        assertThat(colorAt("Camisas formales").allowedCount()).isEqualTo(2);
        assertThat(origins(colorAt("Camisas formales"))).containsExactly("Ropa de hombre:4", "Camisas:2");
        assertThat(colorAt("Pantalones").allowedCount()).isEqualTo(4);
    }

    @Test
    void refusesAValueExcludedAbove() {
        save("Ropa de hombre", "0001", "0002", "0004", "0005");

        assertThatThrownBy(() -> save("Camisas", "0002", "0003"))
                .isInstanceOf(ValueExcludedAboveException.class)
                .hasMessage("El valor «Verde» (0003) no está permitido por el filtro de «Ropa de hombre». Solo puedes"
                        + " incluir valores permitidos arriba.");
        assertThat(filters.find(id("Camisas"), color.systemId())).isEmpty();
    }

    @Test
    void laterNarrowingAboveRestrictsBelowAndIsReported() {
        save("Ropa de hombre", "0001", "0002", "0004", "0005");
        save("Camisas", "0002", "0005");

        save("Ropa de hombre", "0002", "0004");
        ValueFilterDetails camisas = find("Camisas");

        assertThat(labels(camisas.values().allowed())).containsExactly("Azul");
        assertThat(camisas.values().excludedAbove())
                .extracting(excluded -> excluded.value().label() + " por "
                        + excluded.excludedBy().name())
                .containsExactly("Blanco por Ropa de hombre");
        assertThat(filters.find(id("Camisas"), color.systemId()).orElseThrow().valueIds())
                .containsExactly("0002", "0005");

        save("Ropa de hombre", "0004");
        AllowedValues none = find("Camisas").values();
        assertThat(none.allowed()).isEmpty();
        assertThat(none.noneAllowed()).isTrue();
        assertThat(none.involved())
                .extracting(filter -> filter.node().name())
                .containsExactly("Ropa de hombre", "Camisas");
    }

    // --- User Story 3 ---

    @Test
    void removingAFilterRestoresTheInheritedValues() {
        save("Ropa de hombre", "0001", "0002", "0004", "0005");
        save("Camisas", "0002", "0005");

        service.removeFilter(id("Camisas"), color.systemId(), CAN_CHANGE);

        assertThat(colorAt("Camisas formales").allowedCount()).isEqualTo(4);
        assertThatThrownBy(() -> service.removeFilter(id("Camisas"), color.systemId(), CAN_CHANGE))
                .isInstanceOf(FilterNotFoundException.class)
                .hasMessage("El nodo «Camisas» no tiene un filtro para el atributo «Color».");
        assertThatThrownBy(() -> service.removeFilter(id("Ropa de hombre"), color.systemId(), NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);
    }

    @Test
    void movedNodesFollowTheFiltersOfTheirNewBranch() {
        save("Ropa de hombre", "0001", "0002", "0004", "0005");

        service.move(id("Camisas"), id("Ropa de mujer"), CAN_CHANGE);
        assertThat(colorAt("Camisas").allowedCount()).isEqualTo(7);
        assertThat(colorAt("Camisas formales").allowedCount()).isEqualTo(7);

        save("Calzado", "0004", "0005");
        save("Camisas", "0002", "0005");
        service.move(id("Camisas"), id("Calzado"), CAN_CHANGE);

        assertThat(labels(find("Camisas").values().allowed())).containsExactly("Blanco");
    }

    @Test
    void deletingANodeDeletesItsFilters() {
        save("Camisas formales", "0001");

        service.delete(id("Camisas formales"), CAN_CHANGE);

        assertThat(filters.all()).isEmpty();
    }

    // --- Concurrency (edge case "Concurrent changes", SC-002) ---

    @Test
    void twentySimultaneousSavesLeaveOneWholeFilter() throws Exception {
        List<List<String>> requests = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> List.of(COLORES[index % 7][0], COLORES[(index + 3) % 7][0]))
                .toList();

        runTogether(20, index -> save("Ropa de hombre", requests.get(index).toArray(String[]::new)));

        assertThat(filters.all()).hasSize(1);
        List<String> stored = filters.all().getFirst().valueIds();
        assertThat(requests).anySatisfy(request -> assertThat(stored).containsExactlyInAnyOrderElementsOf(request));
    }

    @Test
    void aSaveRacingANarrowingAboveNeverStoresAnExcludedValue() throws Exception {
        for (int round = 0; round < 25; round++) {
            save("Ropa de hombre", "0001", "0002", "0004", "0005");
            filters.delete(id("Camisas"), color.systemId());

            runTogether(2, index -> {
                try {
                    if (index == 0) {
                        save("Ropa de hombre", "0002");
                    } else {
                        save("Camisas", "0005");
                    }
                } catch (ValueExcludedAboveException refused) {
                    // The narrowing came first: Blanco is excluded above, so nothing is stored.
                }
            });

            Optional<com.pim.backend.taxonomy.model.ValueFilter> camisas =
                    filters.find(id("Camisas"), color.systemId());
            // If the save came first it is kept, now excluded above (FR-010); it never applies (allowed is empty).
            camisas.ifPresent(
                    filter -> assertThat(find("Camisas").values().allowed()).isEmpty());
        }
    }

    private interface IndexedTask {
        void run(int index) throws Exception;
    }

    private static void runTogether(int tasks, IndexedTask task) throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(tasks);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < tasks; index++) {
                int current = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    task.run(current);
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void filtersOfAnAttributeThatStopsApplyingAreKeptWithoutEffect() {
        save("Camisas", "0002");

        service.removeAttribute(id("Ropa"), color.systemId(), CAN_CHANGE);
        assertThat(service.find(id("Camisas")).listAttributes()).isEmpty();
        assertThat(filters.find(id("Camisas"), color.systemId())).isPresent();

        service.addAttributes(id("Ropa"), List.of(color.systemId()), CAN_CHANGE);
        assertThat(colorAt("Camisas").allowedCount()).isEqualTo(1);
    }
}
