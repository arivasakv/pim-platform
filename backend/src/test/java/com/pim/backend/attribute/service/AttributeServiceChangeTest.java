package com.pim.backend.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeChangeNotPermittedException;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDetails;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.AttributeInGroupsException;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributeOnTaxonomyNodesException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.AttributeScope;
import com.pim.backend.attribute.model.DisplayNameInUseException;
import com.pim.backend.attribute.model.GroupReference;
import com.pim.backend.attribute.model.ImmutableFieldException;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.model.NumberFormat;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.model.TaxonomyNodeReference;
import com.pim.backend.attribute.repository.FakeAttributeGroups;
import com.pim.backend.attribute.repository.FakeTaxonomyAssignments;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Consulting, editing and deleting attributes (feature 007 FR-004 to FR-018; research R-04, R-05, R-07). */
class AttributeServiceChangeTest {

    private static final Instant NOW = Instant.parse("2026-11-11T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);
    private static final ManualIdPermissions MANUAL_ATTRIBUTE_IDS = ManualIdPermissions.fromCodes("ATTRIBUTE");
    private static final GroupReference LOGISTICOS = new GroupReference("g1", "Logísticos");
    private static final GroupReference VENTAS = new GroupReference("g2", "Ventas");

    private final InMemorySystemIdRegistry systemIds = new InMemorySystemIdRegistry();
    private final InMemoryObjectTypeConfigurationRepository objectTypes =
            new InMemoryObjectTypeConfigurationRepository();
    private final IdentifierService identifierService = newIdentifierService();
    private final InMemoryAttributeRepository attributes = new InMemoryAttributeRepository();
    private final AttributeServiceTest.TestCatalog catalog = new AttributeServiceTest.TestCatalog();
    private final FakeAttributeGroups groups = new FakeAttributeGroups();
    private final FakeTaxonomyAssignments taxonomy = new FakeTaxonomyAssignments();
    private final AttributeService service = new AttributeService(
            attributes, catalog, groups, taxonomy, identifierService, Clock.fixed(NOW, ZoneOffset.UTC));

    private IdentifierService newIdentifierService() {
        new DefaultObjectTypeConfigurations(objectTypes).afterPropertiesSet();
        return new IdentifierService(systemIds, objectTypes, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- User Story 1: consult ---

    @Test
    void listsEveryAttributeWithItsGroupsAndListName() {
        catalog.holds("LOV-COLOR");
        Attribute peso = createNumeric("Peso", 2);
        createListOfValues("Color", SelectionMode.MULTIPLE, "LOV-COLOR");
        createBoolean("Activo");
        groups.put(peso.systemId().toLowerCase(), LOGISTICOS, VENTAS);

        assertThat(service.findAllDetails())
                .extracting(details -> details.attribute().displayName().value())
                .containsExactly("Activo", "Color", "Peso");
        AttributeDetails pesoDetails = service.findDetails(peso.systemId());
        assertThat(pesoDetails.groups()).containsExactly(LOGISTICOS, VENTAS);
        assertThat(pesoDetails.listOfValuesName()).isEmpty();
        assertThat(service.findAllDetails().get(1).listOfValuesName()).contains("lov-color");
        assertThat(service.findAllDetails().getFirst().groups()).isEmpty();
    }

    @Test
    void readsOneAttributeIgnoringCaseAndReportsUnknownOnes() {
        createBoolean("Activo");

        assertThat(service.findDetails("atr000001").attribute().displayName().value())
                .isEqualTo("Activo");
        assertThatThrownBy(() -> service.findDetails("ATR999")).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void leavesTheListNameEmptyWhenTheListIsUnknown() {
        catalog.holds("LOV-COLOR");
        Attribute color = createListOfValues("Color", SelectionMode.SINGLE, "LOV-COLOR");
        catalog.holds("LOV-OTRA");

        assertThat(service.findDetails(color.systemId()).listOfValuesName()).isEmpty();
    }

    // --- User Story 2: edit ---

    @Test
    void editsTheNameAndPropertiesAndKeepsEverythingElse() {
        Attribute peso = createNumeric("Peso", 2);
        groups.put(peso.systemId(), LOGISTICOS);

        AttributeDetails edited = service.update(
                peso.systemId(),
                new AttributeDraft(null, " Peso neto ", AttributeDataType.NUMERIC, numeric(3)),
                CAN_CHANGE);

        Attribute saved = edited.attribute();
        assertThat(saved.systemId()).isEqualTo(peso.systemId());
        assertThat(saved.displayName().value()).isEqualTo("Peso neto");
        assertThat(saved.properties()).isEqualTo(numeric(3));
        assertThat(saved.dataType()).isEqualTo(AttributeDataType.NUMERIC);
        assertThat(saved.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(saved.createdAt()).isEqualTo(peso.createdAt());
        assertThat(saved.updatedAt()).contains(NOW);
        assertThat(edited.groups()).containsExactly(LOGISTICOS);
        assertThat(service.find(peso.systemId())).isEqualTo(saved);
        assertThat(systemIds.count()).isEqualTo(1);
    }

    @Test
    void refusesAnEditWithoutThePermission() {
        Attribute peso = createNumeric("Peso", 2);

        assertThatThrownBy(() -> service.update(peso.systemId(), numericDraft("Otro", 3), NO_PERMISSION))
                .isInstanceOf(AttributeChangeNotPermittedException.class)
                .hasMessage("No tienes permiso para modificar atributos.");
        assertThat(service.find(peso.systemId())).isEqualTo(peso);
    }

    @Test
    void refusesAnEditOfAnUnknownAttribute() {
        assertThatThrownBy(() -> service.update("ATR999", numericDraft("Peso", 2), CAN_CHANGE))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void neverChangesTheSystemIdOrTheDataType() {
        Attribute peso = createNumeric("Peso", 2);

        assertThatThrownBy(() -> service.update(
                        peso.systemId(),
                        new AttributeDraft("OTRO1", "Peso", AttributeDataType.NUMERIC, numeric(2)),
                        CAN_CHANGE))
                .isInstanceOfSatisfying(
                        ImmutableFieldException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AttributeErrorCode.SYSTEM_ID_IMMUTABLE));
        assertThatThrownBy(() -> service.update(
                        peso.systemId(), new AttributeDraft(null, "Peso", AttributeDataType.BOOLEAN, null), CAN_CHANGE))
                .isInstanceOfSatisfying(
                        ImmutableFieldException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AttributeErrorCode.DATA_TYPE_IMMUTABLE));
        assertThat(service.find(peso.systemId())).isEqualTo(peso);

        // The attribute's own ID, in any letter case, is accepted.
        AttributeDraft ownId =
                new AttributeDraft(peso.systemId().toLowerCase(), "Peso", AttributeDataType.NUMERIC, numeric(4));
        assertThat(service.update(peso.systemId(), ownId, CAN_CHANGE)
                        .attribute()
                        .properties())
                .isEqualTo(numeric(4));
    }

    @Test
    void keepsTheDisplayNameUniqueAmongTheOtherAttributes() {
        Attribute peso = createNumeric("Peso", 2);
        createBoolean("Color");

        assertThatThrownBy(() -> service.update(peso.systemId(), numericDraft(" color ", 2), CAN_CHANGE))
                .isInstanceOf(DisplayNameInUseException.class);
        assertThat(service.update(peso.systemId(), numericDraft("PESO", 2), CAN_CHANGE)
                        .attribute()
                        .displayName()
                        .value())
                .isEqualTo("PESO");
        // After a real rename the old name is free again.
        service.update(peso.systemId(), numericDraft("Peso bruto", 2), CAN_CHANGE);
        assertThat(createNumeric("Peso", 1).displayName().value()).isEqualTo("Peso");
    }

    @Test
    void validatesTheDisplayNameAsCreationDoes() {
        Attribute peso = createNumeric("Peso", 2);

        assertThatThrownBy(() -> service.update(peso.systemId(), numericDraft("  ", 2), CAN_CHANGE))
                .isInstanceOfSatisfying(
                        InvalidAttributeException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AttributeErrorCode.INVALID_DISPLAY_NAME));
    }

    @Test
    void relinksAListOfValuesAttributeOnlyToAnExistingList() {
        catalog.holds("LOV-COLOR", "LOV-TALLA");
        Attribute color = createListOfValues("Color", SelectionMode.MULTIPLE, "LOV-COLOR");

        AttributeDetails relinked =
                service.update(color.systemId(), listDraft("Color", SelectionMode.SINGLE, "LOV-TALLA"), CAN_CHANGE);

        assertThat(relinked.attribute().properties())
                .isEqualTo(new ListOfValuesProperties(SelectionMode.SINGLE, "LOV-TALLA"));
        assertThat(relinked.listOfValuesName()).contains("lov-talla");
        assertThat(service.findLinkedToList("LOV-COLOR")).isEmpty();
        assertThat(service.findLinkedToList("LOV-TALLA")).hasSize(1);
        assertThatThrownBy(() -> service.update(
                        color.systemId(), listDraft("Color", SelectionMode.SINGLE, "LOV-OTRA"), CAN_CHANGE))
                .isInstanceOfSatisfying(
                        ListOfValuesUnavailableException.class,
                        exception ->
                                assertThat(exception.code()).isEqualTo(AttributeErrorCode.LIST_OF_VALUES_NOT_FOUND));
        assertThatThrownBy(() -> service.update(color.systemId(), listDraft("Color", null, "LOV-TALLA"), CAN_CHANGE))
                .isInstanceOfSatisfying(
                        InvalidAttributeException.class,
                        exception -> assertThat(exception.field()).isEqualTo("listOfValuesProperties.selectionMode"));
    }

    @Test
    void checksInTheDocumentedOrder() {
        Attribute peso = createNumeric("Peso", 2);
        createBoolean("Color");

        // Forbidden before not found.
        assertThatThrownBy(() -> service.update("ATR999", numericDraft("Peso", 2), NO_PERMISSION))
                .isInstanceOf(AttributeChangeNotPermittedException.class);
        // Not found before immutable fields.
        assertThatThrownBy(() -> service.update(
                        "ATR999", new AttributeDraft(null, "Peso", AttributeDataType.BOOLEAN, null), CAN_CHANGE))
                .isInstanceOf(AttributeNotFoundException.class);
        // Immutable fields before the display name check.
        assertThatThrownBy(() -> service.update(
                        peso.systemId(),
                        new AttributeDraft(null, "Color", AttributeDataType.BOOLEAN, null),
                        CAN_CHANGE))
                .isInstanceOf(ImmutableFieldException.class);
    }

    // --- User Story 3: delete ---

    @Test
    void deletesAnAttributeThatBelongsToNoGroup() {
        Attribute activo = createBoolean("Activo");

        service.delete(activo.systemId().toLowerCase(), CAN_CHANGE);

        assertThat(attributes.count()).isZero();
        assertThatThrownBy(() -> service.find(activo.systemId())).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void refusesToDeleteAnAttributeInGroupsAndNamesThem() {
        Attribute peso = createNumeric("Peso", 2);
        groups.put(peso.systemId(), LOGISTICOS, VENTAS);

        assertThatThrownBy(() -> service.delete(peso.systemId(), CAN_CHANGE))
                .isInstanceOfSatisfying(AttributeInGroupsException.class, exception -> {
                    assertThat(exception.groups()).containsExactly(LOGISTICOS, VENTAS);
                    assertThat(exception.getMessage())
                            .isEqualTo("El atributo pertenece a 2 grupos: Logísticos, Ventas. "
                                    + "Quítalo de esos grupos antes de eliminarlo.");
                });
        assertThat(service.find(peso.systemId())).isEqualTo(peso);

        groups.put(peso.systemId(), LOGISTICOS);
        assertThatThrownBy(() -> service.delete(peso.systemId(), CAN_CHANGE))
                .hasMessage("El atributo pertenece a 1 grupo: Logísticos. Quítalo de esos grupos antes de eliminarlo.");
    }

    @Test
    void refusesToDeleteAnAttributeOnTaxonomyNodesAndNamesThem() {
        Attribute color = createBoolean("Color");
        TaxonomyNodeReference ropa = new TaxonomyNodeReference("CLS000001", "Ropa");
        TaxonomyNodeReference calzado = new TaxonomyNodeReference("CLS000002", "Calzado");
        taxonomy.put(color.systemId().toLowerCase(), ropa, calzado);

        assertThatThrownBy(() -> service.delete(color.systemId(), CAN_CHANGE))
                .isInstanceOfSatisfying(AttributeOnTaxonomyNodesException.class, exception -> {
                    assertThat(exception.nodes()).containsExactly(ropa, calzado);
                    assertThat(exception.getMessage())
                            .isEqualTo("El atributo pertenece a 2 nodos de la taxonomía: Ropa, Calzado. "
                                    + "Quítalo de esos nodos antes de eliminarlo.");
                });
        assertThat(service.find(color.systemId())).isEqualTo(color);

        taxonomy.put(color.systemId(), ropa);
        assertThatThrownBy(() -> service.delete(color.systemId(), CAN_CHANGE))
                .hasMessage("El atributo pertenece a 1 nodo de la taxonomía: Ropa. "
                        + "Quítalo de esos nodos antes de eliminarlo.");

        taxonomy.put(color.systemId());
        service.delete(color.systemId(), CAN_CHANGE);
        assertThat(attributes.count()).isZero();
    }

    @Test
    void checksGroupsBeforeTaxonomyNodes() {
        Attribute peso = createNumeric("Peso", 2);
        groups.put(peso.systemId(), LOGISTICOS);
        taxonomy.put(peso.systemId(), new TaxonomyNodeReference("CLS000001", "Ropa"));

        assertThatThrownBy(() -> service.delete(peso.systemId(), CAN_CHANGE))
                .isInstanceOf(AttributeInGroupsException.class);
        assertThat(service.find(peso.systemId())).isEqualTo(peso);
    }

    @Test
    void refusesADeletionWithoutThePermissionOrOfAnUnknownAttribute() {
        Attribute activo = createBoolean("Activo");

        assertThatThrownBy(() -> service.delete(activo.systemId(), NO_PERMISSION))
                .isInstanceOf(AttributeChangeNotPermittedException.class);
        assertThat(attributes.count()).isEqualTo(1);
        assertThatThrownBy(() -> service.delete("ATR999", CAN_CHANGE)).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void deletingAListOfValuesAttributeLeavesTheListAlone() {
        catalog.holds("LOV-COLOR");
        Attribute color = createListOfValues("Color", SelectionMode.SINGLE, "LOV-COLOR");

        service.delete(color.systemId(), CAN_CHANGE);

        assertThat(catalog.exists("LOV-COLOR")).isTrue();
        assertThat(service.runIfNoAttributeLinksTo("LOV-COLOR", () -> {})).isTrue();
    }

    @Test
    void neverAssignsADeletedAttributesIdAgain() {
        Attribute first = createBoolean("Activo");
        service.delete(first.systemId(), CAN_CHANGE);

        assertThat(createBoolean("Nuevo").systemId()).isNotEqualTo(first.systemId());
        assertThatThrownBy(() -> service.create(
                        new AttributeDraft(first.systemId(), "Manual", AttributeDataType.BOOLEAN, null),
                        CAN_CHANGE,
                        MANUAL_ATTRIBUTE_IDS))
                .isInstanceOf(SystemIdInUseException.class);
    }

    private Attribute createNumeric(String name, int precision) {
        return service.create(numericDraft(name, precision), CAN_CHANGE, NO_MANUAL_IDS);
    }

    private Attribute createBoolean(String name) {
        return service.create(
                new AttributeDraft(null, name, AttributeDataType.BOOLEAN, null), CAN_CHANGE, NO_MANUAL_IDS);
    }

    private Attribute createListOfValues(String name, SelectionMode mode, String listId) {
        return service.create(listDraft(name, mode, listId), CAN_CHANGE, NO_MANUAL_IDS);
    }

    private static AttributeDraft numericDraft(String name, int precision) {
        return new AttributeDraft(null, name, AttributeDataType.NUMERIC, numeric(precision));
    }

    private static AttributeDraft listDraft(String name, SelectionMode mode, String listId) {
        return new AttributeDraft(
                null, name, AttributeDataType.LIST_OF_VALUES, new ListOfValuesProperties(mode, listId));
    }

    private static NumericProperties numeric(int precision) {
        return new NumericProperties(NumberFormat.DECIMAL, precision, BigDecimal.ZERO, null);
    }
}
