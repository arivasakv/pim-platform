package com.pim.backend.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeCreateNotPermittedException;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.AttributeScope;
import com.pim.backend.attribute.model.DisplayNameInUseException;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.ListOfValuesCatalog;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Attribute creation rules of {@link AttributeService}, with feature 003's real {@link IdentifierService} on its
 * in-memory repositories (the repositories are only wired here, never called by the attribute code).
 */
class AttributeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T18:00:00Z");
    private static final AttributePermissions CAN_CREATE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);
    private static final ManualIdPermissions MANUAL_ATTRIBUTE_IDS = ManualIdPermissions.fromCodes("ATTRIBUTE");

    private final InMemorySystemIdRegistry systemIds = new InMemorySystemIdRegistry();
    private final InMemoryObjectTypeConfigurationRepository objectTypes =
            new InMemoryObjectTypeConfigurationRepository();
    private final IdentifierService identifierService = newIdentifierService();
    private final InMemoryAttributeRepository attributes = new InMemoryAttributeRepository();
    private final TestCatalog catalog = new TestCatalog();
    private final AttributeService service =
            new AttributeService(attributes, catalog, identifierService, Clock.fixed(NOW, ZoneOffset.UTC));

    private IdentifierService newIdentifierService() {
        new DefaultObjectTypeConfigurations(objectTypes).afterPropertiesSet();
        return new IdentifierService(systemIds, objectTypes, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- User Story 1 ---

    @Test
    void createsABooleanAttributeWithAnIdFromFeature003() {
        Attribute attribute = createBoolean("Apto para regalo");

        assertThat(attribute.systemId()).isEqualTo("ATR000001");
        assertThat(attribute.displayName().value()).isEqualTo("Apto para regalo");
        assertThat(attribute.dataType()).isEqualTo(AttributeDataType.BOOLEAN);
        assertThat(attribute.scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(attribute.createdAt()).isEqualTo(NOW);
        assertThat(identifierService.find("ATR000001").objectType()).isEqualTo(ObjectType.ATTRIBUTE);
        assertThat(identifierService.find("ATR000001").keyFieldValues()).isEmpty();
    }

    @Test
    void refusesAUserWithoutThePermissionAndRegistersNothing() {
        assertThatThrownBy(() -> service.create(
                        draft(null, "Apto para regalo"), AttributePermissions.fromCodes(null), NO_MANUAL_IDS))
                .isInstanceOf(AttributeCreateNotPermittedException.class);
        assertThat(attributes.count()).isZero();
        assertThat(systemIds.count()).isZero();
    }

    @Test
    void validatesTheDisplayNameAndTheDescription() {
        assertRejected(draft(null, "   "), AttributeErrorCode.INVALID_DISPLAY_NAME);
        assertRejected(draft(null, "x".repeat(121)), AttributeErrorCode.INVALID_DISPLAY_NAME);
        assertRejected(
                new AttributeDraft(null, "Peso", "d".repeat(1001), AttributeDataType.BOOLEAN, null),
                AttributeErrorCode.INVALID_DESCRIPTION);
        assertThat(systemIds.count()).isZero();
    }

    @Test
    void refusesADuplicateDisplayNameWithoutUsingAnId() {
        createBoolean("Apto para regalo");

        assertThatThrownBy(() -> createBoolean(" apto PARA regalo "))
                .isInstanceOf(DisplayNameInUseException.class)
                .hasMessageContaining("apto PARA regalo");
        assertThat(systemIds.count()).isEqualTo(1);
        assertThat(createBoolean("Otro").systemId()).isEqualTo("ATR000002");
    }

    @Test
    void acceptsAManualIdWithFeature003Permission() {
        Attribute attribute = service.create(draft("ATR-COLOR", "Color"), CAN_CREATE, MANUAL_ATTRIBUTE_IDS);

        assertThat(attribute.systemId()).isEqualTo("ATR-COLOR");
    }

    @Test
    void passesOnFeature003RejectionsAndSavesNothing() {
        assertThatThrownBy(() -> service.create(draft("ATR-COLOR", "Color"), CAN_CREATE, NO_MANUAL_IDS))
                .isInstanceOf(ManualIdNotPermittedException.class);
        assertThat(attributes.count()).isZero();

        service.create(draft("ATR-COLOR", "Color"), CAN_CREATE, MANUAL_ATTRIBUTE_IDS);
        assertThatThrownBy(() -> service.create(draft("atr-color", "Talla"), CAN_CREATE, MANUAL_ATTRIBUTE_IDS))
                .isInstanceOf(SystemIdInUseException.class);
        assertThat(attributes.count()).isEqualTo(1);
    }

    @Test
    void findsAnAttributeIgnoringCase() {
        createBoolean("Apto para regalo");

        assertThat(service.find("atr000001").displayName().value()).isEqualTo("Apto para regalo");
        assertThatThrownBy(() -> service.find("ATR999")).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void sendsTheDisplayNameOnlyWhenFeature003DeclaresIt() {
        identifierService.replaceConfiguration(
                ObjectType.ATTRIBUTE,
                identifierService.findConfiguration(ObjectType.ATTRIBUTE).sequence(),
                new KeyFieldDeclaration(List.of("displayName")));

        createBoolean("Apto para regalo");

        assertThat(identifierService.find("ATR000001").keyFieldValues())
                .containsExactly(Map.entry("displayName", "Apto para regalo"));
    }

    // --- User Story 3 ---

    @Test
    void refusesEveryListOfValuesAttributeWhileNoListsExist() {
        for (ListOfValuesProperties properties : new ListOfValuesProperties[] {
            new ListOfValuesProperties(SelectionMode.SINGLE, "LOV-X"), new ListOfValuesProperties(null, null)
        }) {
            assertThatThrownBy(() -> createListOfValues("Color", properties))
                    .isInstanceOfSatisfying(
                            ListOfValuesUnavailableException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo(AttributeErrorCode.LIST_OF_VALUES_NOT_AVAILABLE));
        }
        assertThat(systemIds.count()).isZero();
        assertThat(attributes.count()).isZero();
    }

    @Test
    void checksTheSelectionModeTheListAndItsExistenceOnceListsExist() {
        catalog.holds("LOV-COLOR");

        assertThatThrownBy(() -> createListOfValues("Color", new ListOfValuesProperties(null, "LOV-COLOR")))
                .isInstanceOfSatisfying(InvalidAttributeException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(AttributeErrorCode.REQUIRED);
                    assertThat(exception.field()).isEqualTo("listOfValuesProperties.selectionMode");
                });
        assertThatThrownBy(() -> createListOfValues("Color", new ListOfValuesProperties(SelectionMode.SINGLE, null)))
                .isInstanceOfSatisfying(InvalidAttributeException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(AttributeErrorCode.REQUIRED);
                    assertThat(exception.field()).isEqualTo("listOfValuesProperties.listOfValuesId");
                });
        assertThatThrownBy(
                        () -> createListOfValues("Color", new ListOfValuesProperties(SelectionMode.SINGLE, "LOV-OTRA")))
                .isInstanceOfSatisfying(
                        ListOfValuesUnavailableException.class,
                        exception ->
                                assertThat(exception.code()).isEqualTo(AttributeErrorCode.LIST_OF_VALUES_NOT_FOUND));
        assertThat(systemIds.count()).isZero();

        Attribute color = createListOfValues("Color", new ListOfValuesProperties(SelectionMode.MULTIPLE, "LOV-COLOR"));
        Attribute shade = createListOfValues("Tono", new ListOfValuesProperties(SelectionMode.SINGLE, "LOV-COLOR"));

        assertThat(color.properties()).isEqualTo(new ListOfValuesProperties(SelectionMode.MULTIPLE, "LOV-COLOR"));
        assertThat(shade.systemId()).isEqualTo("ATR000002");
    }

    // --- User Story 5 ---

    @Test
    void everyCreatedAttributeIsGlobal() {
        catalog.holds("LOV-COLOR");

        assertThat(createBoolean("Apto").scope()).isEqualTo(AttributeScope.GLOBAL);
        assertThat(createListOfValues("Color", new ListOfValuesProperties(SelectionMode.SINGLE, "LOV-COLOR"))
                        .scope())
                .isEqualTo(AttributeScope.GLOBAL);
    }

    private Attribute createListOfValues(String name, ListOfValuesProperties properties) {
        return service.create(
                new AttributeDraft(null, name, null, AttributeDataType.LIST_OF_VALUES, properties),
                CAN_CREATE,
                NO_MANUAL_IDS);
    }

    private Attribute createBoolean(String name) {
        return service.create(draft(null, name), CAN_CREATE, NO_MANUAL_IDS);
    }

    private static AttributeDraft draft(String systemId, String name) {
        return new AttributeDraft(systemId, name, null, AttributeDataType.BOOLEAN, null);
    }

    private void assertRejected(AttributeDraft draft, AttributeErrorCode code) {
        assertThatThrownBy(() -> service.create(draft, CAN_CREATE, NO_MANUAL_IDS))
                .isInstanceOfSatisfying(
                        InvalidAttributeException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    /** A list-of-values catalog whose lists the tests choose. */
    static final class TestCatalog implements ListOfValuesCatalog {

        private Set<String> lists = Set.of();

        void holds(String... ids) {
            lists = Set.of(ids);
        }

        @Override
        public boolean hasAny() {
            return !lists.isEmpty();
        }

        @Override
        public boolean exists(String listOfValuesId) {
            return lists.contains(listOfValuesId);
        }
    }
}
