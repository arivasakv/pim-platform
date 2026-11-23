package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceExhaustedException;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.model.BulkLoadOutdatedException;
import com.pim.backend.lov.model.BulkLoadResult;
import com.pim.backend.lov.model.BulkLoadSummary;
import com.pim.backend.lov.model.ChangeOperation;
import com.pim.backend.lov.model.ChangeRecord;
import com.pim.backend.lov.model.InvalidImportException;
import com.pim.backend.lov.model.ListChangeNotPermittedException;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.PlannedListChange;
import com.pim.backend.lov.model.PlannedListChange.Action;
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

/** Bulk loads of several lists through {@link ListOfValuesService} (feature 013 FR-008–FR-011, FR-015; D-2–D-4). */
class ListOfValuesServiceBulkLoadTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);
    private static final byte[] FILE = ("Lista,ID,Etiqueta,Equivalencia\n"
                    + "Color,AZU,Azul marino,\n"
                    + "Color,VER,Verde,GREEN\n"
                    + "Talla,0001,Chica,S\n"
                    + "talla,,Mediana,\n")
            .getBytes(StandardCharsets.UTF_8);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryObjectTypeConfigurationRepository objectTypes =
            new InMemoryObjectTypeConfigurationRepository();
    private final List<ChangeRecord> records = new ArrayList<>();

    /** Fails the {@code failAt}-th write (insert or update), to check that the load is undone. */
    private static final class FailingLists extends InMemoryListOfValuesRepository {
        private int writes;
        private int failAt = Integer.MAX_VALUE;

        @Override
        public void insert(ListOfValues list) {
            fail();
            super.insert(list);
        }

        @Override
        public void update(ListOfValues list) {
            fail();
            super.update(list);
        }

        private void fail() {
            if (++writes == failAt) {
                throw new IllegalStateException("Storage is down");
            }
        }
    }

    private final FailingLists lists = new FailingLists();
    private final IdentifierService identifierService = newIdentifierService();
    private final ListOfValuesService service = new ListOfValuesService(
            lists,
            identifierService,
            new AttributeService(
                    new InMemoryAttributeRepository(),
                    new ListOfValuesCatalogAdapter(new ListOfValuesLookup(lists)),
                    new NoAttributeGroups(),
                    new NoTaxonomyAssignments(),
                    identifierService,
                    clock),
            new ChangeRecorder((ChangeRecordRepository) records::add, clock),
            clock);

    private IdentifierService newIdentifierService() {
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        return new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
    }

    /** List «Color» with ROJ «Rojo» and AZU «Azul». */
    private ListOfValues color() {
        String id =
                service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS).list().systemId();
        service.addValue(id, "ROJ", "Rojo", null, CAN_CHANGE);
        service.addValue(id, "AZU", "Azul", "BLUE", CAN_CHANGE);
        return lists.findById(id).orElseThrow();
    }

    @Test
    void checkingSummarizesTheFileWithoutSavingOrAssigningIds() {
        ListOfValues color = color();

        BulkLoadSummary summary = service.checkBulkLoad(FILE, CAN_CHANGE);

        assertThat(summary.lists())
                .containsExactly(
                        new PlannedListChange("Color", Optional.of(color.systemId()), Action.UPDATE, 1, 1),
                        new PlannedListChange("Talla", Optional.empty(), Action.CREATE, 2, 0));
        assertThat(summary.listsToCreate()).isEqualTo(1);
        assertThat(summary.valuesToAdd()).isEqualTo(3);
        assertThat(summary.checkToken()).hasSize(64);
        assertThat(lists.findAll()).containsExactly(color);
        assertThat(service.create("Marca", null, CAN_CHANGE, NO_MANUAL_IDS)
                        .list()
                        .systemId())
                .as("no ID was used by the check")
                .isEqualTo("LOV000002");
    }

    @Test
    void applyingTheCheckedFileCreatesAndUpdatesEveryList() {
        ListOfValues color = color();
        String token = service.checkBulkLoad(FILE, CAN_CHANGE).checkToken();

        BulkLoadResult result = service.applyBulkLoad(FILE, token, "ana", CAN_CHANGE);

        assertThat(result.lists())
                .containsExactly(
                        new PlannedListChange("Color", Optional.of(color.systemId()), Action.UPDATE, 1, 1),
                        new PlannedListChange("Talla", Optional.of("LOV000002"), Action.CREATE, 2, 0));
        assertThat(lists.findById(color.systemId()).orElseThrow().values())
                .extracting(value -> value.id().value(), ListValue::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ROJ", "Rojo"),
                        org.assertj.core.groups.Tuple.tuple("AZU", "Azul marino"),
                        org.assertj.core.groups.Tuple.tuple("VER", "Verde"));
        ListOfValues talla = lists.findByName(ListName.of("Talla")).orElseThrow();
        assertThat(talla.systemId()).isEqualTo("LOV000002");
        assertThat(talla.equivalencesEnabled()).isTrue();
        assertThat(talla.values()).extracting(value -> value.id().value()).containsExactly("0001", "0002");
        assertThat(records)
                .filteredOn(record -> record.operation() == ChangeOperation.VALUES_IMPORTED)
                .extracting(ChangeRecord::listName, ChangeRecord::user)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Color", "ana"),
                        org.assertj.core.groups.Tuple.tuple("Talla", "ana"));
    }

    @Test
    void refusesAnOutdatedTokenWithoutSavingAnything() {
        ListOfValues color = color();
        String token = service.checkBulkLoad(FILE, CAN_CHANGE).checkToken();
        service.update(color.systemId(), "Color", true, CAN_CHANGE);
        ListOfValues changed = lists.findById(color.systemId()).orElseThrow();

        assertThatThrownBy(() -> service.applyBulkLoad(FILE, token, "ana", CAN_CHANGE))
                .isInstanceOf(BulkLoadOutdatedException.class);
        assertThatThrownBy(() -> service.applyBulkLoad(FILE, "otro", "ana", CAN_CHANGE))
                .isInstanceOf(BulkLoadOutdatedException.class);
        assertThat(lists.findAll()).containsExactly(changed);
    }

    @Test
    void refusesWhenAListTheFileWouldCreateWasCreatedMeanwhile() {
        String token = service.checkBulkLoad(FILE, CAN_CHANGE).checkToken();
        service.create("Talla", null, CAN_CHANGE, NO_MANUAL_IDS);

        assertThatThrownBy(() -> service.applyBulkLoad(FILE, token, "ana", CAN_CHANGE))
                .isInstanceOf(BulkLoadOutdatedException.class);
    }

    @Test
    void reportsInvalidRowsOnCheckAndApply() {
        byte[] wrong = "Lista,ID,Etiqueta\nColor,ROJ,\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.checkBulkLoad(wrong, CAN_CHANGE)).isInstanceOf(InvalidImportException.class);
        assertThatThrownBy(() -> service.applyBulkLoad(wrong, "x", "ana", CAN_CHANGE))
                .isInstanceOf(InvalidImportException.class);
        assertThat(lists.findAll()).isEmpty();
    }

    @Test
    void needsThePermissionToCheckAndToApply() {
        assertThatThrownBy(() -> service.checkBulkLoad(FILE, NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.applyBulkLoad(FILE, "x", "ana", NO_PERMISSION))
                .isInstanceOf(ListChangeNotPermittedException.class);
    }

    @Test
    void savesNothingWhenNoListIdIsLeft() {
        color();
        String token = service.checkBulkLoad(FILE, CAN_CHANGE).checkToken();
        objectTypes.save(objectTypes
                .find(ObjectType.LIST_OF_VALUES)
                .withSequence(objectTypes
                        .find(ObjectType.LIST_OF_VALUES)
                        .sequence()
                        .withNextValue(SequenceConfiguration.MAX_VALUE)));
        service.create("Ocupa el último ID", null, CAN_CHANGE, NO_MANUAL_IDS);
        List<ListOfValues> before = lists.findAll();

        assertThatThrownBy(() -> service.applyBulkLoad(FILE, token, "ana", CAN_CHANGE))
                .isInstanceOf(SequenceExhaustedException.class);
        assertThat(lists.findAll()).isEqualTo(before);
    }

    @Test
    void undoesEveryWriteWhenOneFails() {
        ListOfValues color = color();
        String token = service.checkBulkLoad(FILE, CAN_CHANGE).checkToken();
        lists.writes = 0;
        lists.failAt = 2; // the new list «Talla» is inserted, then replacing «Color» fails

        assertThatThrownBy(() -> service.applyBulkLoad(FILE, token, "ana", CAN_CHANGE))
                .isInstanceOf(IllegalStateException.class);
        lists.failAt = Integer.MAX_VALUE;
        assertThat(lists.findAll()).containsExactly(color);
        assertThat(records).noneMatch(record -> record.operation() == ChangeOperation.VALUES_IMPORTED);
    }
}
