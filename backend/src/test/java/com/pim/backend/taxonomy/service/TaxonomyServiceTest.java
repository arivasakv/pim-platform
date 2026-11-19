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
import com.pim.backend.taxonomy.model.AddAttributesOutcome;
import com.pim.backend.taxonomy.model.AttributeNotDirectException;
import com.pim.backend.taxonomy.model.AttributeNotOnNodeException;
import com.pim.backend.taxonomy.model.NodeAttribute;
import com.pim.backend.taxonomy.model.NodeDetails;
import com.pim.backend.taxonomy.model.NodeNameInUseException;
import com.pim.backend.taxonomy.model.NodeNotEmptyException;
import com.pim.backend.taxonomy.model.NodeNotFoundException;
import com.pim.backend.taxonomy.model.NodeReference;
import com.pim.backend.taxonomy.model.NodeSummary;
import com.pim.backend.taxonomy.model.TaxonomyChangeNotPermittedException;
import com.pim.backend.taxonomy.model.TaxonomyErrorCode;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import com.pim.backend.taxonomy.model.UnknownAttributeException;
import com.pim.backend.taxonomy.repository.InMemoryTaxonomyNodeRepository;
import com.pim.backend.taxonomy.repository.InMemoryValueFilterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Hierarchy, assignment and inheritance rules of {@link TaxonomyService}, with feature 004's real {@link
 * AttributeService} and feature 003's real {@link IdentifierService} on in-memory test fakes.
 */
class TaxonomyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AttributeService attributeService;
    private final IdentifierService identifierService;
    private final InMemoryTaxonomyNodeRepository nodes = new InMemoryTaxonomyNodeRepository();
    private final TaxonomyService service;

    {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
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
    }

    private Attribute createAttribute(String name) {
        return attributeService.create(
                new AttributeDraft(null, name, null, AttributeDataType.BOOLEAN, null),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
    }

    private String create(String name, String parentId) {
        return service.create(name, null, parentId, CAN_CHANGE).node().systemId();
    }

    private static List<String> names(NodeDetails details) {
        return details.attributes().stream().map(NodeAttribute::displayName).toList();
    }

    // --- User Story 1: hierarchy ---

    @Test
    void createsTopLevelAndChildNodesWithClassificationIds() {
        NodeDetails ropa = service.create("  Ropa ", " Prendas ", null, CAN_CHANGE);
        NodeDetails hombre =
                service.create("Ropa de hombre", null, ropa.node().systemId().toLowerCase(), CAN_CHANGE);

        assertThat(ropa.node().systemId()).isEqualTo("CLS000001");
        assertThat(ropa.node().name().value()).isEqualTo("Ropa");
        assertThat(ropa.node().description()).contains("Prendas");
        assertThat(ropa.level()).isEqualTo(1);
        assertThat(ropa.path()).isEmpty();
        assertThat(ropa.node().createdAt()).isEqualTo(NOW);
        assertThat(hombre.node().systemId()).isEqualTo("CLS000002");
        assertThat(hombre.node().parentId()).contains("CLS000001");
        assertThat(hombre.level()).isEqualTo(2);
        assertThat(hombre.path()).containsExactly(new NodeReference("CLS000001", "Ropa"));
        assertThat(service.find("CLS000001").children())
                .containsExactly(new NodeReference("CLS000002", "Ropa de hombre"));
    }

    @Test
    void listsEveryNodeWithLevelAndChildCount() {
        String ropa = create("Ropa", null);
        String hombre = create("Ropa de hombre", ropa);
        create("Camisas", hombre);
        create("Pantalones", hombre);

        assertThat(service.findAll())
                .extracting(summary -> summary.node().name().value(), NodeSummary::level, NodeSummary::childCount)
                .containsExactly(
                        tuple("Camisas", 3, 0),
                        tuple("Pantalones", 3, 0),
                        tuple("Ropa", 1, 1),
                        tuple("Ropa de hombre", 2, 2));
    }

    @Test
    void refusesAnUnknownParent() {
        assertThatThrownBy(() -> service.create("Camisas", null, "CLS999999", CAN_CHANGE))
                .isInstanceOfSatisfying(NodeNotFoundException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("parentId");
                    assertThat(exception.getMessage()).isEqualTo("No existe un nodo con el ID CLS999999.");
                });
        assertThat(nodes.findAll()).isEmpty();
    }

    @Test
    void namesAreUniqueAmongSiblingsIgnoringCaseAndSpaces() {
        String ropa = create("Ropa", null);
        String calzado = create("Calzado", null);
        create("Accesorios", ropa);

        assertThatThrownBy(() -> service.create(" ACCESORIOS ", null, ropa, CAN_CHANGE))
                .isInstanceOf(NodeNameInUseException.class)
                .hasMessage("Ya existe un nodo «ACCESORIOS» en este nivel. Usa otro nombre.");
        assertThatThrownBy(() -> service.create("ropa", null, null, CAN_CHANGE))
                .isInstanceOf(NodeNameInUseException.class);
        assertThat(service.create("Accesorios", null, calzado, CAN_CHANGE).level())
                .isEqualTo(2);
    }

    @Test
    void renamesKeepingParentChildrenAndAttributes() {
        Attribute color = createAttribute("Color");
        String ropa = create("Ropa", null);
        String hombre = create("Ropa de hombre", ropa);
        service.addAttributes(hombre, List.of(color.systemId()), CAN_CHANGE);
        create("Camisas", hombre);

        NodeDetails renamed = service.update(hombre, "Caballero", "Línea masculina", CAN_CHANGE);

        assertThat(renamed.node().name().value()).isEqualTo("Caballero");
        assertThat(renamed.node().description()).contains("Línea masculina");
        assertThat(renamed.node().parentId()).contains(ropa);
        assertThat(renamed.children()).extracting(NodeReference::name).containsExactly("Camisas");
        assertThat(renamed.node().attributeIds()).containsExactly(color.systemId());
        assertThat(renamed.node().updatedAt()).contains(NOW);
    }

    @Test
    void renamingToASiblingsNameIsRefusedButKeepingItsOwnNameIsNot() {
        String ropa = create("Ropa", null);
        create("Calzado", null);

        assertThatThrownBy(() -> service.update(ropa, "CALZADO", null, CAN_CHANGE))
                .isInstanceOf(NodeNameInUseException.class);
        assertThat(service.update(ropa, "ROPA", null, CAN_CHANGE).node().name().value())
                .isEqualTo("ROPA");
    }

    @Test
    void deletesOnlyNodesWithoutChildrenOrDirectAttributes() {
        Attribute color = createAttribute("Color");
        String ropa = create("Ropa", null);
        String camisas = create("Camisas", ropa);
        service.addAttributes(camisas, List.of(color.systemId()), CAN_CHANGE);

        assertThatThrownBy(() -> service.delete(ropa, CAN_CHANGE))
                .isInstanceOfSatisfying(NodeNotEmptyException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.NODE_HAS_CHILDREN);
                    assertThat(exception.getMessage())
                            .isEqualTo("El nodo «Ropa» tiene 1 nodo hijo. Elimínalos antes de eliminar este nodo.");
                });
        assertThatThrownBy(() -> service.delete(camisas, CAN_CHANGE))
                .isInstanceOfSatisfying(NodeNotEmptyException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.NODE_HAS_ATTRIBUTES);
                    assertThat(exception.getMessage())
                            .isEqualTo(
                                    "El nodo «Camisas» tiene 1 atributo asignado. Quítalos antes de eliminar el nodo.");
                });

        service.removeAttribute(camisas, color.systemId(), CAN_CHANGE);
        service.delete(camisas.toLowerCase(), CAN_CHANGE);
        service.delete(ropa, CAN_CHANGE);

        assertThat(nodes.findAll()).isEmpty();
        assertThat(attributeService.find(color.systemId())).isEqualTo(color);
    }

    @Test
    void everyChangeNeedsThePermissionAndChangesNothingWithoutIt() {
        Attribute color = createAttribute("Color");
        String ropa = create("Ropa", null);

        assertThatThrownBy(() -> service.create("Calzado", null, null, NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class)
                .hasMessage("No tienes permiso para administrar la taxonomía.");
        assertThatThrownBy(() -> service.update(ropa, "Otro", null, NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.delete(ropa, NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.addAttributes(ropa, List.of(color.systemId()), NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.removeAttribute(ropa, color.systemId(), NO_PERMISSION))
                .isInstanceOf(TaxonomyChangeNotPermittedException.class);

        assertThat(nodes.findAll()).extracting(node -> node.name().value()).containsExactly("Ropa");
        assertThat(service.find(ropa).node().attributeIds()).isEmpty();
    }

    @Test
    void anUnknownNodeIsNotFound() {
        assertThatThrownBy(() -> service.find("CLS999999"))
                .isInstanceOfSatisfying(
                        NodeNotFoundException.class,
                        exception -> assertThat(exception.field()).isEqualTo("nodeId"));
    }

    // --- User Story 2: assignments ---

    @Test
    void assignsAllOrNothingAndReportsRepeats() {
        Attribute color = createAttribute("Color");
        Attribute material = createAttribute("Material");
        String ropa = create("Ropa", null);

        AddAttributesOutcome first =
                service.addAttributes(ropa, List.of(color.systemId().toLowerCase(), material.systemId()), CAN_CHANGE);
        AddAttributesOutcome second = service.addAttributes(
                ropa, List.of(material.systemId(), color.systemId(), color.systemId()), CAN_CHANGE);

        assertThat(first.added()).containsExactly(color.systemId(), material.systemId());
        assertThat(first.details().node().attributeIds()).containsExactly(color.systemId(), material.systemId());
        assertThat(second.added()).isEmpty();
        assertThat(second.alreadyAssigned()).containsExactly(material.systemId(), color.systemId());
        assertThatThrownBy(() -> service.addAttributes(ropa, List.of("ATR999999"), CAN_CHANGE))
                .isInstanceOf(UnknownAttributeException.class)
                .hasMessage("No existe un atributo con el ID ATR999999.");
        Attribute marca = createAttribute("Marca");
        assertThatThrownBy(() -> service.addAttributes(ropa, List.of(marca.systemId(), "ATR999999"), CAN_CHANGE))
                .isInstanceOf(UnknownAttributeException.class);
        assertThat(service.find(ropa).node().attributeIds()).containsExactly(color.systemId(), material.systemId());
    }

    @Test
    void assignmentsNeverChangeTheAttribute() {
        Attribute color = createAttribute("Color");
        String ropa = create("Ropa", null);

        service.addAttributes(ropa, List.of(color.systemId()), CAN_CHANGE);
        service.removeAttribute(ropa, color.systemId(), CAN_CHANGE);

        assertThat(attributeService.find(color.systemId())).isEqualTo(color);
    }

    @Test
    void removesOnlyDirectAssignments() {
        Attribute color = createAttribute("Color");
        Attribute talla = createAttribute("Talla");
        String ropa = create("Ropa", null);
        String camisas = create("Camisas", ropa);
        service.addAttributes(ropa, List.of(color.systemId()), CAN_CHANGE);

        assertThatThrownBy(() -> service.removeAttribute(camisas, color.systemId(), CAN_CHANGE))
                .isInstanceOf(AttributeNotDirectException.class)
                .hasMessage("El atributo «Color» se hereda de «Ropa». Quítalo en ese nodo.");
        assertThatThrownBy(() -> service.removeAttribute(camisas, talla.systemId(), CAN_CHANGE))
                .isInstanceOf(AttributeNotOnNodeException.class);

        service.removeAttribute(ropa, color.systemId().toLowerCase(), CAN_CHANGE);
        assertThat(service.find(camisas).attributes()).isEmpty();
    }

    // --- User Story 3: effective attributes ---

    @Test
    void returnsTheFeatureRequestExampleWithNamesTypesAndOrigins() {
        Attribute color = createAttribute("Color");
        Attribute material = createAttribute("Material");
        Attribute marca = createAttribute("Marca");
        Attribute genero = createAttribute("Género");
        Attribute manga = createAttribute("Largo de manga");
        Attribute cintura = createAttribute("Talla de cintura");
        String ropa = create("Ropa", null);
        String hombre = create("Ropa de hombre", ropa);
        String camisas = create("Camisas", hombre);
        String pantalones = create("Pantalones", hombre);
        service.addAttributes(ropa, List.of(color.systemId(), material.systemId(), marca.systemId()), CAN_CHANGE);
        service.addAttributes(hombre, List.of(genero.systemId()), CAN_CHANGE);
        service.addAttributes(camisas, List.of(manga.systemId()), CAN_CHANGE);
        service.addAttributes(pantalones, List.of(cintura.systemId()), CAN_CHANGE);

        NodeDetails details = service.find(camisas);

        assertThat(details.attributes())
                .extracting(
                        NodeAttribute::displayName,
                        attribute -> attribute.effective().assignment(),
                        attribute -> attribute.effective().origin().name(),
                        NodeAttribute::dataType)
                .containsExactly(
                        tuple("Color", INHERITED, "Ropa", "BOOLEAN"),
                        tuple("Material", INHERITED, "Ropa", "BOOLEAN"),
                        tuple("Marca", INHERITED, "Ropa", "BOOLEAN"),
                        tuple("Género", INHERITED, "Ropa de hombre", "BOOLEAN"),
                        tuple("Largo de manga", DIRECT, "Camisas", "BOOLEAN"));
        assertThat(names(service.find(pantalones)))
                .containsExactly("Color", "Material", "Marca", "Género", "Talla de cintura");
        assertThat(details.path()).extracting(NodeReference::name).containsExactly("Ropa", "Ropa de hombre");
        assertThat(details.level()).isEqualTo(3);
    }

    @Test
    void showsTheCurrentAttributeName() {
        Attribute color = createAttribute("Color");
        String ropa = create("Ropa", null);
        service.addAttributes(ropa, List.of(color.systemId()), CAN_CHANGE);
        attributeService.update(
                color.systemId(),
                new AttributeDraft(color.systemId(), "Color principal", null, AttributeDataType.BOOLEAN, null),
                CAN_CHANGE);

        assertThat(names(service.find(ropa))).containsExactly("Color principal");
    }

    // --- User Story 4: propagation ---

    @Test
    void ancestorChangesReachEveryDescendantOnTheNextRead() {
        Attribute temporada = createAttribute("Temporada");
        String ropa = create("Ropa", null);
        String hombre = create("Ropa de hombre", ropa);
        String camisas = create("Camisas", hombre);
        String formales = create("Camisas formales", camisas);

        service.addAttributes(ropa, List.of(temporada.systemId()), CAN_CHANGE);
        List<String> withIt = new ArrayList<>();
        for (String node : List.of(hombre, camisas, formales)) {
            NodeAttribute only = service.find(node).attributes().getFirst();
            assertThat(only.effective().assignment()).isEqualTo(INHERITED);
            assertThat(only.effective().origin().systemId()).isEqualTo(ropa);
            withIt.add(node);
        }
        assertThat(withIt).hasSize(3);

        service.update(ropa, "Ropa y accesorios", null, CAN_CHANGE);
        assertThat(service.find(formales)
                        .attributes()
                        .getFirst()
                        .effective()
                        .origin()
                        .name())
                .isEqualTo("Ropa y accesorios");

        service.removeAttribute(ropa, temporada.systemId(), CAN_CHANGE);
        assertThat(service.find(formales).attributes()).isEmpty();
    }

    @Test
    void aDirectAttributeAlsoOnAnAncestorIsShownOnceUntilItsDirectAssignmentGoes() {
        Attribute genero = createAttribute("Género");
        String hombre = create("Ropa de hombre", null);
        String camisas = create("Camisas", hombre);
        service.addAttributes(hombre, List.of(genero.systemId()), CAN_CHANGE);
        service.addAttributes(camisas, List.of(genero.systemId()), CAN_CHANGE);

        NodeAttribute both = service.find(camisas).attributes().getFirst();
        assertThat(service.find(camisas).attributes()).hasSize(1);
        assertThat(both.effective().assignment()).isEqualTo(DIRECT);
        assertThat(both.effective().alsoInheritedFrom()).contains(new NodeReference(hombre, "Ropa de hombre"));

        service.removeAttribute(camisas, genero.systemId(), CAN_CHANGE);
        NodeAttribute inherited = service.find(camisas).attributes().getFirst();
        assertThat(inherited.effective().assignment()).isEqualTo(INHERITED);
        assertThat(inherited.effective().alsoInheritedFrom()).isEqualTo(Optional.empty());
    }

    // --- FR-021: the attribute deletion check ---

    @Test
    void runsTheActionOnlyWhenNoNodeListsTheAttribute() {
        Attribute color = createAttribute("Color");
        Attribute talla = createAttribute("Talla");
        String ropa = create("Ropa", null);
        String calzado = create("Calzado", null);
        service.addAttributes(ropa, List.of(color.systemId()), CAN_CHANGE);
        service.addAttributes(calzado, List.of(color.systemId()), CAN_CHANGE);
        List<String> ran = new ArrayList<>();

        List<NodeReference> listing = service.runIfOnNoNode(color.systemId().toLowerCase(), () -> ran.add("color"));
        List<NodeReference> none = service.runIfOnNoNode(talla.systemId(), () -> ran.add("talla"));

        assertThat(listing).extracting(NodeReference::name).containsExactly("Calzado", "Ropa");
        assertThat(none).isEmpty();
        assertThat(ran).containsExactly("talla");
        assertThat(TaxonomyNode.key("cls000001")).isEqualTo("CLS000001");
    }
}
