package com.pim.backend.identifier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.AssignmentMethod;
import com.pim.backend.identifier.model.AssignmentOutcome;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceExhaustedException;
import com.pim.backend.identifier.model.SequenceKind;
import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.identifier.model.SystemIdNotFoundException;
import com.pim.backend.identifier.model.SystemIdRegistration;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Identifier rules of {@link IdentifierService}, with in-memory repositories seeded with the defaults. */
class IdentifierServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T18:00:00Z");
    private static final ManualIdPermissions NO_PERMISSIONS = ManualIdPermissions.fromCodes(null);

    private final InMemorySystemIdRegistry registry = new InMemorySystemIdRegistry();
    private final InMemoryObjectTypeConfigurationRepository configurations =
            new InMemoryObjectTypeConfigurationRepository();
    private final IdentifierService service = newService();

    private IdentifierService newService() {
        new DefaultObjectTypeConfigurations(configurations).afterPropertiesSet();
        return new IdentifierService(registry, configurations, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- User Story 1: automatic IDs (FR-002, FR-005, FR-006) ---

    @Test
    void generatesConsecutiveIdsFromTheTypeSequence() {
        AssignmentResult first = assignAutomatic(ObjectType.ATTRIBUTE);
        AssignmentResult second = assignAutomatic(ObjectType.ATTRIBUTE);

        assertThat(first.outcome()).isEqualTo(AssignmentOutcome.ASSIGNED);
        assertThat(first.registration().systemId().value()).isEqualTo("ATR000001");
        assertThat(first.registration().assignment()).isEqualTo(AssignmentMethod.AUTOMATIC);
        assertThat(first.registration().objectType()).isEqualTo(ObjectType.ATTRIBUTE);
        assertThat(first.registration().registeredAt()).isEqualTo(NOW);
        assertThat(second.registration().systemId().value()).isEqualTo("ATR000002");
    }

    @Test
    void eachTypeUsesItsOwnSequence() {
        assignAutomatic(ObjectType.ATTRIBUTE);

        assertThat(assignAutomatic(ObjectType.TEMPLATE)
                        .registration()
                        .systemId()
                        .value())
                .isEqualTo("PLT000001");
    }

    @Test
    void treatsABlankIdAsNoIdAndNeedsNoPermission() {
        AssignmentResult result = service.assign(ObjectType.PRODUCT, "   ", Map.of(), NO_PERMISSIONS);

        assertThat(result.registration().systemId().value()).isEqualTo("PRD000001");
        assertThat(result.registration().assignment()).isEqualTo(AssignmentMethod.AUTOMATIC);
    }

    @Test
    void skipsValuesAlreadyUsed() {
        assignAutomatic(ObjectType.ATTRIBUTE);
        assignAutomatic(ObjectType.ATTRIBUTE);
        registry.add(new SystemIdRegistration(
                SystemId.of("ATR000003"), ObjectType.ATTRIBUTE, Map.of(), AssignmentMethod.MANUAL, NOW));

        assertThat(assignAutomatic(ObjectType.ATTRIBUTE)
                        .registration()
                        .systemId()
                        .value())
                .isEqualTo("ATR000004");
        assertThat(assignAutomatic(ObjectType.ATTRIBUTE)
                        .registration()
                        .systemId()
                        .value())
                .isEqualTo("ATR000005");
    }

    @Test
    void failsWithoutSavingWhenTheSequenceIsExhausted() {
        configurations.save(configurations
                .find(ObjectType.PRODUCT)
                .withSequence(configurations
                        .find(ObjectType.PRODUCT)
                        .sequence()
                        .withNextValue(SequenceConfiguration.MAX_VALUE)));

        assertThat(assignAutomatic(ObjectType.PRODUCT).registration().systemId().value())
                .isEqualTo("PRD999999999999999999");
        assertThatThrownBy(() -> assignAutomatic(ObjectType.PRODUCT)).isInstanceOf(SequenceExhaustedException.class);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void findsARegistrationIgnoringCase() {
        assignAutomatic(ObjectType.ATTRIBUTE);

        assertThat(service.find("atr000001").systemId().value()).isEqualTo("ATR000001");
    }

    @Test
    void reportsAnUnknownId() {
        assertThatThrownBy(() -> service.find("XYZ-1")).isInstanceOf(SystemIdNotFoundException.class);
        assertThatThrownBy(() -> service.find("not valid")).isInstanceOf(SystemIdNotFoundException.class);
    }

    // --- User Story 2: manual IDs (FR-001, FR-003, FR-004; D-6) ---

    @Test
    void registersAManualIdWithThePermissionOfItsType() {
        AssignmentResult result = service.assign(
                ObjectType.CLASSIFICATION, " CLS-ROPA ", Map.of(), ManualIdPermissions.fromCodes("CLASSIFICATION"));

        assertThat(result.outcome()).isEqualTo(AssignmentOutcome.ASSIGNED);
        assertThat(result.registration().systemId().value()).isEqualTo("CLS-ROPA");
        assertThat(result.registration().assignment()).isEqualTo(AssignmentMethod.MANUAL);
    }

    @Test
    void rejectsAManualIdWithoutThePermissionOfItsTypeAndSavesNothing() {
        for (ManualIdPermissions permissions : new ManualIdPermissions[] {NO_PERMISSIONS, product()}) {
            assertThatThrownBy(() -> service.assign(ObjectType.CLASSIFICATION, "CLS-OTRA", Map.of(), permissions))
                    .isInstanceOfSatisfying(ManualIdNotPermittedException.class, exception -> {
                        assertThat(exception.objectType()).isEqualTo(ObjectType.CLASSIFICATION);
                        assertThat(exception.requestedSystemId()).isEqualTo("CLS-OTRA");
                    });
        }
        assertThat(registry.count()).isZero();
    }

    @Test
    void checksThePermissionBeforeTheFormat() {
        assertThatThrownBy(() -> service.assign(ObjectType.PRODUCT, "A B", Map.of(), NO_PERMISSIONS))
                .isInstanceOf(ManualIdNotPermittedException.class);
        assertThatThrownBy(() -> service.assign(ObjectType.PRODUCT, "A B", Map.of(), product()))
                .isInstanceOfSatisfying(
                        InvalidIdentifierRequestException.class,
                        exception -> assertThat(exception.code()).isEqualTo(IdentifierErrorCode.INVALID_SYSTEM_ID));
    }

    @Test
    void rejectsAnIdUsedByAnObjectOfAnyTypeIgnoringCase() {
        service.assign(
                ObjectType.CLASSIFICATION, "CLS-ROPA", Map.of(), ManualIdPermissions.fromCodes("CLASSIFICATION"));

        assertThatThrownBy(() -> service.assign(ObjectType.PRODUCT, "cls-ropa", Map.of(), product()))
                .isInstanceOfSatisfying(
                        SystemIdInUseException.class,
                        exception -> assertThat(exception.systemId().value()).isEqualTo("cls-ropa"));
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void acceptsAManualIdInTheSequenceFormatAndTheGeneratorSkipsIt() {
        service.assign(ObjectType.ATTRIBUTE, "ATR000001", Map.of(), ManualIdPermissions.fromCodes("ATTRIBUTE"));

        assertThat(assignAutomatic(ObjectType.ATTRIBUTE)
                        .registration()
                        .systemId()
                        .value())
                .isEqualTo("ATR000002");
    }

    // --- User Story 3: a saved ID never changes (FR-007) ---

    @Test
    void aRegistrationCannotBeChanged() {
        SystemIdRegistration registration =
                assignAutomatic(ObjectType.ATTRIBUTE).registration();

        assertThatThrownBy(() -> registration.keyFieldValues().put("code", "X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.find("ATR000001").keyFieldValues().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theServiceOffersNoWayToChangeARegistration() {
        Set<String> publicMethods = Arrays.stream(IdentifierService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(publicMethods)
                .isSubsetOf("assign", "find", "findConfigurations", "findConfiguration", "replaceConfiguration");
    }

    // --- User Story 4: reusing an existing ID (FR-009, FR-013; D-8, D-10) ---

    @Test
    void reusingTheIdOfTheSameObjectReturnsItAndCreatesNothing() {
        declareAttributeKeyFields("code");
        AssignmentResult first = assignAttribute("A-100", Map.of("code", "COLOR"));

        AssignmentResult again = assignAttribute("A-100", Map.of("code", "COLOR"));
        AssignmentResult trimmed = assignAttribute("A-100", Map.of("code", " COLOR "));

        assertThat(first.outcome()).isEqualTo(AssignmentOutcome.ASSIGNED);
        assertThat(again.outcome()).isEqualTo(AssignmentOutcome.SAME_OBJECT);
        assertThat(again.registration()).isEqualTo(first.registration());
        assertThat(trimmed.outcome()).isEqualTo(AssignmentOutcome.SAME_OBJECT);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void reusingTheIdWithOtherKeyValuesOrAnotherTypeIsRejected() {
        declareAttributeKeyFields("code");
        SystemIdRegistration original =
                assignAttribute("A-100", Map.of("code", "COLOR")).registration();

        assertThatThrownBy(() -> assignAttribute("A-100", Map.of("code", "TALLA")))
                .isInstanceOf(SystemIdInUseException.class);
        assertThatThrownBy(() -> service.assign(
                        ObjectType.TEMPLATE, "A-100", Map.of(), ManualIdPermissions.fromCodes("TEMPLATE")))
                .isInstanceOf(SystemIdInUseException.class);
        assertThat(service.find("A-100")).isEqualTo(original);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void aTypeWithoutKeyFieldsNeverMatches() {
        assignAutomatic(ObjectType.TEMPLATE);

        assertThatThrownBy(() -> service.assign(
                        ObjectType.TEMPLATE, "PLT000001", Map.of(), ManualIdPermissions.fromCodes("TEMPLATE")))
                .isInstanceOf(SystemIdInUseException.class);
    }

    @Test
    void requiresExactlyTheDeclaredKeyFields() {
        declareAttributeKeyFields("code");

        assertKeyFieldRejected(Map.of(), IdentifierErrorCode.MISSING_KEY_FIELD, "code");
        assertKeyFieldRejected(Map.of("code", "X", "color", "Y"), IdentifierErrorCode.UNKNOWN_KEY_FIELD, "color");
        assertKeyFieldRejected(Map.of("code", "   "), IdentifierErrorCode.INVALID_KEY_FIELD_VALUE, "code");
        assertKeyFieldRejected(Map.of("code", "x".repeat(201)), IdentifierErrorCode.INVALID_KEY_FIELD_VALUE, "code");
        assertThat(assignAttribute("A-1", Map.of("code", "x".repeat(200))).outcome())
                .isEqualTo(AssignmentOutcome.ASSIGNED);
    }

    @Test
    void theAutomaticPathValidatesAndStoresTheKeyFieldsToo() {
        declareAttributeKeyFields("code", "group");

        assertThatThrownBy(() -> service.assign(ObjectType.ATTRIBUTE, null, Map.of("code", "X"), NO_PERMISSIONS))
                .isInstanceOf(InvalidIdentifierRequestException.class);
        SystemIdRegistration registration = service.assign(
                        ObjectType.ATTRIBUTE, null, Map.of("group", " G1 ", "code", "X"), NO_PERMISSIONS)
                .registration();

        assertThat(registration.keyFieldValues()).containsExactly(Map.entry("code", "X"), Map.entry("group", "G1"));
    }

    @Test
    void aTypeWithoutKeyFieldsAcceptsOnlyAnEmptyMap() {
        assertThatThrownBy(() -> service.assign(ObjectType.PRODUCT, null, Map.of("sku", "1"), NO_PERMISSIONS))
                .isInstanceOfSatisfying(
                        InvalidIdentifierRequestException.class,
                        exception -> assertThat(exception.code()).isEqualTo(IdentifierErrorCode.UNKNOWN_KEY_FIELD));
    }

    // --- User Story 5: configuration per object type (FR-006, FR-008, FR-013) ---

    @Test
    void listsTheSixConfigurationsInObjectTypeOrder() {
        assertThat(service.findConfigurations())
                .extracting(ObjectTypeConfiguration::objectType)
                .containsExactly(ObjectType.values());
        assertThat(service.findConfiguration(ObjectType.TEMPLATE).sequence().prefix())
                .isEqualTo("PLT");
    }

    @Test
    void laterIdsFollowANewConfigurationAndSavedIdsDoNotChange() {
        SystemIdRegistration before = assignAutomatic(ObjectType.PRODUCT).registration();

        ObjectTypeConfiguration replaced = service.replaceConfiguration(
                ObjectType.PRODUCT,
                new SequenceConfiguration(SequenceKind.NUMERIC, null, 8, 1000),
                KeyFieldDeclaration.empty());

        assertThat(replaced.sequence().kind()).isEqualTo(SequenceKind.NUMERIC);
        assertThat(assignAutomatic(ObjectType.PRODUCT).registration().systemId().value())
                .isEqualTo("00001000");
        assertThat(service.find("PRD000001")).isEqualTo(before);
    }

    @Test
    void loweringTheNextValueSkipsIdsAlreadyUsed() {
        assignAutomatic(ObjectType.ATTRIBUTE);
        assignAutomatic(ObjectType.ATTRIBUTE);

        service.replaceConfiguration(
                ObjectType.ATTRIBUTE,
                new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "ATR", 6, 1),
                KeyFieldDeclaration.empty());

        assertThat(assignAutomatic(ObjectType.ATTRIBUTE)
                        .registration()
                        .systemId()
                        .value())
                .isEqualTo("ATR000003");
    }

    @Test
    void changingTheKeyFieldsLeavesSavedRegistrationsUnchanged() {
        declareAttributeKeyFields("code");
        SystemIdRegistration saved =
                assignAttribute("A-100", Map.of("code", "COLOR")).registration();

        service.replaceConfiguration(
                ObjectType.ATTRIBUTE,
                configurations.find(ObjectType.ATTRIBUTE).sequence(),
                new KeyFieldDeclaration(List.of("code", "group")));

        assertThat(service.find("A-100")).isEqualTo(saved);
        assertThat(service.find("A-100").keyFieldValues()).containsExactly(Map.entry("code", "COLOR"));
    }

    private void declareAttributeKeyFields(String... names) {
        ObjectTypeConfiguration attribute = configurations.find(ObjectType.ATTRIBUTE);
        configurations.save(new ObjectTypeConfiguration(
                ObjectType.ATTRIBUTE, attribute.sequence(), new KeyFieldDeclaration(List.of(names))));
    }

    private AssignmentResult assignAttribute(String systemId, Map<String, String> keyFieldValues) {
        return service.assign(
                ObjectType.ATTRIBUTE, systemId, keyFieldValues, ManualIdPermissions.fromCodes("ATTRIBUTE"));
    }

    private void assertKeyFieldRejected(Map<String, String> values, IdentifierErrorCode code, String field) {
        assertThatThrownBy(() -> assignAttribute("A-200", values))
                .isInstanceOfSatisfying(InvalidIdentifierRequestException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.field()).isEqualTo(field);
                });
    }

    private static ManualIdPermissions product() {
        return ManualIdPermissions.fromCodes("PRODUCT");
    }

    private AssignmentResult assignAutomatic(ObjectType type) {
        return service.assign(type, null, Map.of(), NO_PERMISSIONS);
    }
}
