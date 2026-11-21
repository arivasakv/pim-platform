package com.pim.backend.taxonomy.service;

import static com.pim.backend.taxonomy.model.AssignmentType.DIRECT;
import static com.pim.backend.taxonomy.model.AssignmentType.INHERITED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.repository.EmptyListOfValuesCatalog;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.repository.InMemoryListOfValuesRepository;
import com.pim.backend.lov.service.ListOfValuesLookup;
import com.pim.backend.taxonomy.model.MoveOutcome;
import com.pim.backend.taxonomy.model.NodeAttribute;
import com.pim.backend.taxonomy.model.NodeDetails;
import com.pim.backend.taxonomy.model.NodeMoveNotAllowedException;
import com.pim.backend.taxonomy.model.NodeNameInUseException;
import com.pim.backend.taxonomy.model.NodeNotFoundException;
import com.pim.backend.taxonomy.model.NodeReference;
import com.pim.backend.taxonomy.model.TaxonomyChangeNotPermittedException;
import com.pim.backend.taxonomy.model.TaxonomyErrorCode;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import com.pim.backend.taxonomy.repository.InMemoryTaxonomyNodeRepository;
import com.pim.backend.taxonomy.repository.InMemoryValueFilterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Moving nodes with {@link TaxonomyService#move} (feature 011 FR-001 to FR-012), on the spec's example hierarchy, with
 * feature 004's real {@link AttributeService} and feature 003's real {@link IdentifierService} on in-memory fakes.
 *
 * <pre>
 * Ropa                      (Color, Material, Marca)
 * ├── Ropa de hombre        (Género)
 * │   ├── Camisas           (Largo de manga)
 * │   │   └── Camisas formales
 * │   └── Pantalones        (Talla de cintura)
 * └── Ropa de mujer         (Género, Corte)
 * Calzado                   (Material de suela)
 * </pre>
 */
class TaxonomyServiceMoveTest {

    private static final Instant NOW = Instant.parse("2026-11-19T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AttributeService attributeService;
    private final InMemoryTaxonomyNodeRepository nodes = new InMemoryTaxonomyNodeRepository();
    private final TaxonomyService service;
    private final Map<String, String> ids = new HashMap<>();
    private final Map<String, Attribute> attributes = new HashMap<>();

    {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        IdentifierService identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        attributeService = new AttributeService(
                new InMemoryAttributeRepository(),
                new EmptyListOfValuesCatalog(),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                clock);
        service = new TaxonomyService(
                nodes,
                new InMemoryValueFilterRepository(),
                attributeService,
                new ListOfValuesLookup(new InMemoryListOfValuesRepository()),
                identifierService,
                clock);
        buildExample();
    }

    private void buildExample() {
        node("Ropa", null, "Color", "Material", "Marca");
        node("Ropa de hombre", "Ropa", "Género");
        node("Camisas", "Ropa de hombre", "Largo de manga");
        node("Camisas formales", "Camisas");
        node("Pantalones", "Ropa de hombre", "Talla de cintura");
        node("Ropa de mujer", "Ropa", "Género", "Corte");
        node("Calzado", null, "Material de suela");
    }

    private void node(String name, String parent, String... attributeNames) {
        String parentId = parent == null ? null : ids.get(parent);
        String id = service.create(name, null, parentId, CAN_CHANGE).node().systemId();
        ids.put(name, id);
        if (attributeNames.length > 0) {
            List<String> attributeIds = java.util.Arrays.stream(attributeNames)
                    .map(this::attribute)
                    .map(Attribute::systemId)
                    .toList();
            service.addAttributes(id, attributeIds, CAN_CHANGE);
        }
    }

    private Attribute attribute(String name) {
        return attributes.computeIfAbsent(
                name,
                key -> attributeService.create(
                        new AttributeDraft(null, key, AttributeDataType.BOOLEAN, null),
                        CAN_CHANGE,
                        ManualIdPermissions.fromCodes(null)));
    }

    private String id(String name) {
        return ids.get(name);
    }

    private MoveOutcome move(String name, String destination) {
        return service.move(id(name), destination == null ? null : id(destination), CAN_CHANGE);
    }

    private TaxonomyNode stored(String name) {
        return nodes.findById(id(name)).orElseThrow();
    }

    private static List<String> names(NodeDetails details) {
        return details.attributes().stream().map(NodeAttribute::displayName).toList();
    }

    private static List<String> pathNames(NodeDetails details) {
        return details.path().stream().map(NodeReference::name).toList();
    }

    // --- User Story 1: move a node with its branch ---

    @Test
    void movesTheNodeWithItsBranchAndKeepsItsData() {
        TaxonomyNode before = stored("Camisas");
        TaxonomyNode childBefore = stored("Camisas formales");

        MoveOutcome outcome = service.move(id("Camisas"), id("Ropa de mujer").toLowerCase(), CAN_CHANGE);

        assertThat(outcome.movedNodeCount()).isEqualTo(2);
        assertThat(outcome.details().level()).isEqualTo(3);
        assertThat(pathNames(outcome.details())).containsExactly("Ropa", "Ropa de mujer");
        TaxonomyNode after = stored("Camisas");
        assertThat(after.parentId()).contains(id("Ropa de mujer"));
        assertThat(after.systemId()).isEqualTo(before.systemId());
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.description()).isEqualTo(before.description());
        assertThat(after.attributeIds()).isEqualTo(before.attributeIds());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).contains(NOW);
        assertThat(stored("Camisas formales")).isEqualTo(childBefore);
        assertThat(service.find(id("Camisas")).children())
                .containsExactly(new NodeReference(id("Camisas formales"), "Camisas formales"));
        assertThat(service.find(id("Ropa de hombre")).children())
                .extracting(NodeReference::name)
                .containsExactly("Pantalones");
    }

    @Test
    void countsEveryNodeOfTheBranch() {
        assertThat(move("Camisas formales", "Pantalones").movedNodeCount()).isEqualTo(1);
        assertThat(move("Ropa de hombre", "Calzado").movedNodeCount()).isEqualTo(4);
    }

    @Test
    void movingNeedsThePermissionAndChangesNothingWithoutIt() {
        List<TaxonomyNode> before = nodes.findAll();

        assertThatThrownBy(() -> service.move(id("Camisas"), id("Ropa de mujer"), NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);
        assertThat(nodes.findAll()).isEqualTo(before);
    }

    // --- User Story 2: refusals ---

    @Test
    void refusesMovingANodeIntoItselfOrItsDescendants() {
        List<TaxonomyNode> before = nodes.findAll();

        for (String destination : List.of("Ropa de hombre", "Camisas", "Camisas formales", "Pantalones")) {
            assertThatThrownBy(() -> move("Ropa de hombre", destination))
                    .as(destination)
                    .isInstanceOfSatisfying(NodeMoveNotAllowedException.class, exception -> {
                        assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.NODE_MOVE_INTO_OWN_BRANCH);
                        assertThat(exception.getMessage())
                                .isEqualTo("No puedes mover «Ropa de hombre» dentro de sí mismo ni de uno de sus"
                                        + " nodos descendientes.");
                    });
        }
        assertThat(nodes.findAll()).isEqualTo(before);
    }

    @Test
    void refusesMovingANodeToWhereItAlreadyIs() {
        List<TaxonomyNode> before = nodes.findAll();

        assertThatThrownBy(() -> move("Camisas", "Ropa de hombre"))
                .isInstanceOfSatisfying(NodeMoveNotAllowedException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.NODE_ALREADY_THERE);
                    assertThat(exception.getMessage()).isEqualTo("«Camisas» ya está en «Ropa de hombre».");
                });
        assertThatThrownBy(() -> move("Ropa", null))
                .isInstanceOfSatisfying(NodeMoveNotAllowedException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.NODE_ALREADY_THERE);
                    assertThat(exception.getMessage()).isEqualTo("«Ropa» ya está en el primer nivel.");
                });
        assertThat(nodes.findAll()).isEqualTo(before);
    }

    @Test
    void refusesANameAlreadyUsedAtTheDestinationIgnoringCaseAndSpaces() {
        service.create(" camisas ", null, id("Ropa de mujer"), CAN_CHANGE);
        List<TaxonomyNode> before = nodes.findAll();

        assertThatThrownBy(() -> move("Camisas", "Ropa de mujer"))
                .isInstanceOfSatisfying(NodeNameInUseException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("parentId");
                    assertThat(exception.getMessage())
                            .isEqualTo("Ya existe un nodo «Camisas» en «Ropa de mujer». Cambia el nombre de uno de"
                                    + " los dos antes de mover.");
                });
        assertThat(nodes.findAll()).isEqualTo(before);
    }

    @Test
    void refusesUnknownNodesNamingTheField() {
        assertThatThrownBy(() -> service.move("CLS999999", id("Calzado"), CAN_CHANGE))
                .isInstanceOfSatisfying(NodeNotFoundException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("nodeId");
                    assertThat(exception.getMessage()).isEqualTo("No existe un nodo con el ID CLS999999.");
                });
        assertThatThrownBy(() -> service.move(id("Camisas"), "CLS999999", CAN_CHANGE))
                .isInstanceOfSatisfying(NodeNotFoundException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("parentId");
                    assertThat(exception.getMessage()).isEqualTo("No existe un nodo con el ID CLS999999.");
                });
        assertThat(stored("Camisas").parentId()).contains(id("Ropa de hombre"));
    }

    // --- User Story 3: inheritance after a move ---

    @Test
    void theMovedBranchInheritsFromItsNewAncestorsOnly() {
        List<NodeAttribute> hombreBefore = service.find(id("Ropa de hombre")).attributes();
        List<NodeAttribute> pantalonesBefore = service.find(id("Pantalones")).attributes();

        move("Camisas", "Calzado");

        NodeDetails camisas = service.find(id("Camisas"));
        assertThat(camisas.attributes())
                .extracting(
                        NodeAttribute::displayName,
                        a -> a.effective().assignment(),
                        a -> a.effective().origin().name())
                .containsExactly(
                        tuple("Material de suela", INHERITED, "Calzado"), tuple("Largo de manga", DIRECT, "Camisas"));
        NodeDetails formales = service.find(id("Camisas formales"));
        assertThat(formales.level()).isEqualTo(3);
        assertThat(pathNames(formales)).containsExactly("Calzado", "Camisas");
        assertThat(formales.attributes())
                .extracting(
                        NodeAttribute::displayName,
                        a -> a.effective().assignment(),
                        a -> a.effective().origin().name())
                .containsExactly(
                        tuple("Material de suela", INHERITED, "Calzado"),
                        tuple("Largo de manga", INHERITED, "Camisas"));
        assertThat(names(formales)).doesNotContain("Color", "Material", "Marca", "Género");
        assertThat(service.find(id("Ropa de hombre")).attributes()).isEqualTo(hombreBefore);
        assertThat(service.find(id("Pantalones")).attributes()).isEqualTo(pantalonesBefore);
    }

    @Test
    void anAttributeDirectOnTheNodeAndOnANewAncestorIsListedOnceAsDirect() {
        service.addAttributes(id("Camisas"), List.of(attribute("Género").systemId()), CAN_CHANGE);

        move("Camisas", "Ropa de mujer");

        NodeDetails camisas = service.find(id("Camisas"));
        NodeAttribute genero = camisas.attributes().stream()
                .filter(attribute -> attribute.displayName().equals("Género"))
                .findFirst()
                .orElseThrow();
        assertThat(names(camisas)).containsOnlyOnce("Género");
        assertThat(genero.effective().assignment()).isEqualTo(DIRECT);
        assertThat(genero.effective().alsoInheritedFrom())
                .contains(new NodeReference(id("Ropa de mujer"), "Ropa de mujer"));
        assertThat(stored("Camisas").hasDirect(attribute("Género").systemId())).isTrue();
    }

    @Test
    void noDepthLimitApplies() {
        move("Calzado", "Camisas formales");

        NodeDetails calzado = service.find(id("Calzado"));
        assertThat(calzado.level()).isEqualTo(5);
        assertThat(names(calzado))
                .containsExactly("Color", "Material", "Marca", "Género", "Largo de manga", "Material de suela");
    }

    // --- User Story 4: top level ---

    @Test
    void movesANodeToTheTopLevel() {
        String child = service.create("Blusas", null, id("Ropa de mujer"), CAN_CHANGE)
                .node()
                .systemId();

        MoveOutcome outcome = service.move(id("Ropa de mujer"), "  ", CAN_CHANGE);

        assertThat(outcome.movedNodeCount()).isEqualTo(2);
        assertThat(outcome.details().level()).isEqualTo(1);
        assertThat(outcome.details().path()).isEmpty();
        assertThat(stored("Ropa de mujer").parentId()).isEmpty();
        assertThat(outcome.details().attributes()).allMatch(a -> a.effective().assignment() == DIRECT);
        NodeDetails blusas = service.find(child);
        assertThat(blusas.level()).isEqualTo(2);
        assertThat(names(blusas)).containsExactly("Género", "Corte");
    }

    @Test
    void refusesTheTopLevelWhenATopLevelNodeHasTheName() {
        service.create("Ropa de mujer", null, null, CAN_CHANGE);

        assertThatThrownBy(() -> move("Ropa de mujer", null))
                .isInstanceOfSatisfying(NodeNameInUseException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("parentId");
                    assertThat(exception.getMessage())
                            .isEqualTo("Ya existe un nodo «Ropa de mujer» en el primer nivel. Cambia el nombre de"
                                    + " uno de los dos antes de mover.");
                });
        assertThat(stored("Ropa de mujer").parentId()).contains(id("Ropa"));
    }
}
