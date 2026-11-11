package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.model.ChangeOperation;
import com.pim.backend.lov.model.ChangeRecord;
import com.pim.backend.lov.model.ImportOutcome;
import com.pim.backend.lov.model.LinkedAttribute;
import com.pim.backend.lov.model.ListChangeNotPermittedException;
import com.pim.backend.lov.model.ListDetails;
import com.pim.backend.lov.model.ListInUseException;
import com.pim.backend.lov.model.ListNameInUseException;
import com.pim.backend.lov.model.ListNotFoundException;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.ValueIdInUseException;
import com.pim.backend.lov.model.ValueNotFoundException;
import com.pim.backend.lov.repository.ChangeRecordRepository;
import com.pim.backend.lov.repository.InMemoryListOfValuesRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Rules of {@link ListOfValuesService}, with feature 003's and feature 004's real services on in-memory test fakes, and
 * the real catalog adapter so attributes can link to the lists created here.
 */
class ListOfValuesServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryListOfValuesRepository lists = new InMemoryListOfValuesRepository();
    private final List<ChangeRecord> records = new ArrayList<>();
    private final IdentifierService identifierService = newIdentifierService();
    private final AttributeService attributeService = new AttributeService(
            new InMemoryAttributeRepository(),
            new ListOfValuesCatalogAdapter(new ListOfValuesLookup(lists)),
            identifierService,
            clock);
    private final ChangeRecordRepository recordRepository = records::add;
    private final ListOfValuesService service = new ListOfValuesService(
            lists, identifierService, attributeService, new ChangeRecorder(recordRepository, clock), clock);

    private IdentifierService newIdentifierService() {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        return new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
    }

    // --- User Story 1: create and add values ---

    @Test
    void createsAnEmptyListWithASystemIdFromFeature003() {
        ListDetails details = service.create(" Color ", null, CAN_CHANGE, NO_MANUAL_IDS);

        ListOfValues list = details.list();
        assertThat(list.systemId()).isEqualTo("LOV000001");
        assertThat(list.name().value()).isEqualTo("Color");
        assertThat(list.values()).isEmpty();
        assertThat(details.attributes()).isEmpty();
        assertThat(identifierService.find("LOV000001").objectType()).isEqualTo(ObjectType.LIST_OF_VALUES);
        assertThat(lists.findById("lov000001")).contains(list);
    }

    @Test
    void refusesChangesWithoutThePermission() {
        assertThatThrownBy(() -> service.create("Color", null, NO_PERMISSION, NO_MANUAL_IDS))
                .isInstanceOf(ListChangeNotPermittedException.class)
                .hasMessage("No tienes permiso para administrar listas de valores.");
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        assertThatThrownBy(() -> service.addValue("LOV000001", "1", "A", null, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.updateValue("LOV000001", "1", "A", null, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.deleteValue("LOV000001", "1", null, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.update("LOV000001", "X", false, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.delete("LOV000001", null, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.importValues("LOV000001", new byte[0], null, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
    }

    @Test
    void aManualSystemIdNeedsFeature003sPermission() {
        assertThatThrownBy(() -> service.create("Color", "LOV-COLOR", CAN_CHANGE, NO_MANUAL_IDS))
                .isInstanceOf(ManualIdNotPermittedException.class);

        ListDetails details =
                service.create("Color", "LOV-COLOR", CAN_CHANGE, ManualIdPermissions.fromCodes("LIST_OF_VALUES"));

        assertThat(details.list().systemId()).isEqualTo("LOV-COLOR");
    }

    @Test
    void refusesADuplicateNameIgnoringCase() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);

        assertThatThrownBy(() -> service.create("COLOR", null, CAN_CHANGE, NO_MANUAL_IDS))
                .isInstanceOf(ListNameInUseException.class)
                .hasMessage("Ya existe una lista con el nombre \"COLOR\". Usa otro nombre.");
    }

    @Test
    void addsValuesWithManualAndGeneratedIds() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);

        ListValue manual = service.addValue("lov000001", "0001", "Azul", null, CAN_CHANGE);
        ListValue generated = service.addValue("LOV000001", null, "Rojo", null, CAN_CHANGE);

        assertThat(manual.id().value()).isEqualTo("0001");
        assertThat(generated.id().value()).isEqualTo("0002");
        assertThatThrownBy(() -> service.addValue("LOV000001", "0001", "Otro", null, CAN_CHANGE))
                .isInstanceOf(ValueIdInUseException.class);
        assertThatThrownBy(() -> service.addValue("LOV999999", "1", "X", null, CAN_CHANGE))
                .isInstanceOf(ListNotFoundException.class)
                .hasMessage("No existe una lista de valores con el ID LOV999999.");
    }

    @Test
    void attributesCanLinkToListsOnceTheyExist() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);

        createLinkedAttribute("Tono", SelectionMode.MULTIPLE, "lov000001");

        assertThat(attributeService.findLinkedToList("LOV000001")).hasSize(1);
    }

    // --- User Story 2: consult ---

    @Test
    void listsWithValueAndAttributeCountsSortedByName() {
        service.create("Talla", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.addValue("LOV000002", null, "Azul", null, CAN_CHANGE);
        createLinkedAttribute("Tono", SelectionMode.MULTIPLE, "LOV000002");
        createLinkedAttribute("Color base", SelectionMode.SINGLE, "lov000002");

        assertThat(service.findAll())
                .extracting(
                        summary -> summary.list().name().value(),
                        summary -> summary.list().values().size(),
                        summary -> summary.attributeCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Color", 1, 2),
                        org.assertj.core.groups.Tuple.tuple("Talla", 0, 0));
    }

    @Test
    void findsOneListWithItsLinkedAttributes() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        createLinkedAttribute("Tono", SelectionMode.MULTIPLE, "LOV000001");
        createLinkedAttribute("Color base", SelectionMode.SINGLE, "LOV000001");

        ListDetails details = service.find("lov000001");

        assertThat(details.attributes())
                .extracting(LinkedAttribute::displayName, LinkedAttribute::selectionMode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Color base", SelectionMode.SINGLE),
                        org.assertj.core.groups.Tuple.tuple("Tono", SelectionMode.MULTIPLE));
        assertThatThrownBy(() -> service.find("LOV999999")).isInstanceOf(ListNotFoundException.class);
    }

    // --- User Story 3: edit and delete ---

    @Test
    void updatesOnlyTheLabelAndEquivalenceOfAValue() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.addValue("LOV000001", "0001", "Azul", null, CAN_CHANGE);

        ListValue updated = service.updateValue("LOV000001", "0001", "AZUL", "BLUE", CAN_CHANGE);

        assertThat(updated).isEqualTo(new ListValue(updated.id(), "AZUL", Optional.of("BLUE")));
        assertThat(updated.id().value()).isEqualTo("0001");
        assertThatThrownBy(() -> service.updateValue("LOV000001", "0009", "X", null, CAN_CHANGE))
                .isInstanceOf(ValueNotFoundException.class);
    }

    @Test
    void deletesAValueAndRecordsIt() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.addValue("LOV000001", "AbC", "Azul", null, CAN_CHANGE);

        service.deleteValue("LOV000001", "abc", "ana", CAN_CHANGE);

        assertThat(service.find("LOV000001").list().values()).isEmpty();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.operation()).isEqualTo(ChangeOperation.VALUE_DELETED);
            assertThat(record.user()).isEqualTo("ana");
            assertThat(record.listId()).isEqualTo("LOV000001");
            assertThat(record.listName()).isEqualTo("Color");
            assertThat(record.valueIds()).containsExactly("AbC");
            assertThat(record.at()).isEqualTo(NOW);
        });
        assertThatThrownBy(() -> service.deleteValue("LOV000001", "abc", null, CAN_CHANGE))
                .isInstanceOf(ValueNotFoundException.class);
    }

    @Test
    void renamesKeepingIdValuesAndLinks() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.create("Talla", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.addValue("LOV000001", "0001", "Azul", null, CAN_CHANGE);
        createLinkedAttribute("Tono", SelectionMode.SINGLE, "LOV000001");

        ListDetails renamed = service.update("LOV000001", "Colores", false, CAN_CHANGE);

        assertThat(renamed.list().systemId()).isEqualTo("LOV000001");
        assertThat(renamed.list().name().value()).isEqualTo("Colores");
        assertThat(renamed.list().values()).hasSize(1);
        assertThat(renamed.attributes()).hasSize(1);
        assertThat(service.update("LOV000001", "COLORES", false, CAN_CHANGE)
                        .list()
                        .name()
                        .value())
                .isEqualTo("COLORES");
        assertThatThrownBy(() -> service.update("LOV000001", "talla", false, CAN_CHANGE))
                .isInstanceOf(ListNameInUseException.class);
    }

    @Test
    void refusesToDeleteAListThatAttributesUse() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        createLinkedAttribute("Tono", SelectionMode.SINGLE, "LOV000001");
        createLinkedAttribute("Color base", SelectionMode.MULTIPLE, "LOV000001");

        assertThatThrownBy(() -> service.delete("LOV000001", null, CAN_CHANGE))
                .isInstanceOf(ListInUseException.class)
                .hasMessage("La lista \"Color\" está vinculada a 2 atributos y no se puede eliminar.");
        assertThat(lists.findById("LOV000001")).isPresent();
        assertThat(records).isEmpty();
    }

    @Test
    void deletesAnUnusedListAndRecordsIt() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);

        service.delete("lov000001", "ana", CAN_CHANGE);

        assertThat(lists.findById("LOV000001")).isEmpty();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.operation()).isEqualTo(ChangeOperation.LIST_DELETED);
            assertThat(record.listName()).isEqualTo("Color");
        });
        assertThatThrownBy(() -> service.delete("LOV000001", null, CAN_CHANGE))
                .isInstanceOf(ListNotFoundException.class);
    }

    // --- User Story 4: bulk load ---

    @Test
    void importsValuesAllOrNothingAndRecordsIt() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.addValue("LOV000001", "0001", "Azul", "BLUE", CAN_CHANGE);

        ImportOutcome outcome = service.importValues(
                "LOV000001",
                "ID;Etiqueta;Equivalencia\n0001;AZUL;\n;Verde;VERDE\n".getBytes(StandardCharsets.UTF_8),
                "ana",
                CAN_CHANGE);

        assertThat(outcome.added()).isEqualTo(1);
        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(service.find("LOV000001").list().values())
                .extracting(
                        value -> value.id().value(),
                        ListValue::label,
                        value -> value.equivalence().orElse(null))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("0001", "AZUL", "BLUE"),
                        org.assertj.core.groups.Tuple.tuple("0002", "Verde", "VERDE"));
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.operation()).isEqualTo(ChangeOperation.VALUES_IMPORTED);
            assertThat(record.added()).isEqualTo(1);
            assertThat(record.updated()).isEqualTo(1);
        });
    }

    // --- User Story 5: equivalences ---

    @Test
    void turningEquivalencesOffKeepsThem() {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        service.update("LOV000001", "Color", true, CAN_CHANGE);
        service.addValue("LOV000001", "1", "Negro intenso", "NEGRO", CAN_CHANGE);
        service.addValue("LOV000001", "2", "Negro mate", "NEGRO", CAN_CHANGE);
        service.updateValue("LOV000001", "1", "Negro intenso", "  ", CAN_CHANGE);

        ListDetails off = service.update("LOV000001", "Color", false, CAN_CHANGE);

        assertThat(off.list().equivalencesEnabled()).isFalse();
        assertThat(off.list().values())
                .extracting(value -> value.equivalence().orElse(null))
                .containsExactly(null, "NEGRO");
    }

    private void createLinkedAttribute(String name, SelectionMode mode, String listId) {
        attributeService.create(
                new AttributeDraft(
                        null, name, null, AttributeDataType.LIST_OF_VALUES, new ListOfValuesProperties(mode, listId)),
                CAN_CHANGE,
                NO_MANUAL_IDS);
    }
}
