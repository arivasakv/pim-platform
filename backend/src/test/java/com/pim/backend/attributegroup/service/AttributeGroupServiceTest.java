package com.pim.backend.attributegroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.repository.EmptyListOfValuesCatalog;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.attributegroup.model.AddAttributesOutcome;
import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.AttributeNotInGroupException;
import com.pim.backend.attributegroup.model.GroupChangeNotPermittedException;
import com.pim.backend.attributegroup.model.GroupDetails;
import com.pim.backend.attributegroup.model.GroupMember;
import com.pim.backend.attributegroup.model.GroupNameInUseException;
import com.pim.backend.attributegroup.model.GroupNotEmptyException;
import com.pim.backend.attributegroup.model.GroupNotFoundException;
import com.pim.backend.attributegroup.model.InvalidGroupException;
import com.pim.backend.attributegroup.model.UnknownAttributeException;
import com.pim.backend.attributegroup.repository.InMemoryAttributeGroupRepository;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Group rules of {@link AttributeGroupService}, with feature 004's real {@link AttributeService} and feature 003's
 * real {@code IdentifierService} on in-memory test fakes.
 */
class AttributeGroupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AttributeService attributeService = newAttributeService();
    private final InMemoryAttributeGroupRepository groups = new InMemoryAttributeGroupRepository();
    private final AttributeGroupService service = new AttributeGroupService(groups, attributeService, clock);

    private AttributeService newAttributeService() {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        IdentifierService identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        return new AttributeService(
                new InMemoryAttributeRepository(),
                new EmptyListOfValuesCatalog(),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                clock);
    }

    // --- User Story 1: create ---

    @Test
    void createsAnEmptyGroupWithAGeneratedIdentifier() {
        GroupDetails details = service.create("  Logísticos ", " Dimensiones ", CAN_CHANGE);

        AttributeGroup group = details.group();
        assertThat(UUID.fromString(group.id())).isNotNull();
        assertThat(group.name().value()).isEqualTo("Logísticos");
        assertThat(group.description()).contains("Dimensiones");
        assertThat(group.attributeIds()).isEmpty();
        assertThat(group.createdAt()).isEqualTo(NOW);
        assertThat(details.members()).isEmpty();
        assertThat(groups.findById(group.id())).contains(group);
    }

    @Test
    void refusesToCreateWithoutThePermission() {
        assertThatThrownBy(() -> service.create("Logísticos", null, NO_PERMISSION))
                .isInstanceOf(GroupChangeNotPermittedException.class)
                .hasMessage("No tienes permiso para administrar grupos de atributos.");
        assertThat(groups.findAll()).isEmpty();
    }

    @Test
    void refusesADuplicateNameIgnoringCaseAndSpaces() {
        service.create("Logísticos", null, CAN_CHANGE);

        assertThatThrownBy(() -> service.create(" logísticos ", null, CAN_CHANGE))
                .isInstanceOf(GroupNameInUseException.class)
                .hasMessage("Ya existe un grupo con el nombre \"logísticos\". Usa otro nombre.");
        assertThat(groups.findAll()).hasSize(1);
    }

    @Test
    void refusesAnInvalidNameBeforeCheckingAnythingElse() {
        assertThatThrownBy(() -> service.create("  ", null, CAN_CHANGE)).isInstanceOf(InvalidGroupException.class);
    }

    @Test
    void listsGroupsSortedByName() {
        service.create("Sistema", null, CAN_CHANGE);
        service.create("compra ventas", null, CAN_CHANGE);
        service.create("Logísticos", null, CAN_CHANGE);

        assertThat(service.findAll())
                .extracting(group -> group.name().value())
                .containsExactly("compra ventas", "Logísticos", "Sistema");
    }

    // --- User Story 2: add attributes ---

    @Test
    void addsExistingAttributesInOrder() {
        Attribute weight = createAttribute("Peso");
        Attribute volume = createAttribute("Volumen");
        String groupId = createGroup("Logísticos");

        AddAttributesOutcome outcome =
                service.addAttributes(groupId, List.of(weight.systemId(), volume.systemId()), CAN_CHANGE);

        assertThat(outcome.added()).containsExactly("ATR000001", "ATR000002");
        assertThat(outcome.alreadyInGroup()).isEmpty();
        assertThat(outcome.details().group().attributeIds()).containsExactly("ATR000001", "ATR000002");
        assertThat(outcome.details().members())
                .containsExactly(
                        new GroupMember("ATR000001", "Peso", "BOOLEAN"),
                        new GroupMember("ATR000002", "Volumen", "BOOLEAN"));
    }

    @Test
    void reportsAnAttributeAlreadyInTheGroupAndDoesNotAddItTwice() {
        createAttribute("Peso");
        String groupId = createGroup("Logísticos");
        service.addAttributes(groupId, List.of("ATR000001"), CAN_CHANGE);

        AddAttributesOutcome outcome = service.addAttributes(groupId, List.of("atr000001"), CAN_CHANGE);

        assertThat(outcome.added()).isEmpty();
        assertThat(outcome.alreadyInGroup()).containsExactly("ATR000001");
        assertThat(outcome.details().group().attributeIds()).containsExactly("ATR000001");
    }

    @Test
    void addsNothingWhenOneAttributeIsUnknown() {
        createAttribute("Peso");
        String groupId = createGroup("Logísticos");

        assertThatThrownBy(() -> service.addAttributes(groupId, List.of("ATR000001", "ATR999"), CAN_CHANGE))
                .isInstanceOfSatisfying(UnknownAttributeException.class, exception -> {
                    assertThat(exception.attributeId()).isEqualTo("ATR999");
                    assertThat(exception.getMessage()).isEqualTo("No existe un atributo con el ID ATR999.");
                });
        assertThat(service.find(groupId).group().attributeIds()).isEmpty();
    }

    @Test
    void anAttributeCanBeInSeveralGroups() {
        createAttribute("Peso");
        String logistics = createGroup("Logísticos");
        String sales = createGroup("Compra Ventas");

        service.addAttributes(logistics, List.of("ATR000001"), CAN_CHANGE);
        service.addAttributes(sales, List.of("ATR000001"), CAN_CHANGE);

        assertThat(service.findAllContaining("atr000001"))
                .extracting(AttributeGroup::id)
                .containsExactlyInAnyOrder(logistics, sales);
    }

    @Test
    void addingNeverChangesTheAttribute() {
        Attribute before = createAttribute("Peso");
        String groupId = createGroup("Logísticos");

        service.addAttributes(groupId, List.of("ATR000001"), CAN_CHANGE);

        assertThat(attributeService.find("ATR000001")).isEqualTo(before);
        assertThat(attributeService.find("ATR000001").displayName().value()).isEqualTo("Peso");
    }

    @Test
    void refusesToAddWithoutThePermissionOrToAnUnknownGroup() {
        createAttribute("Peso");
        String groupId = createGroup("Logísticos");

        assertThatThrownBy(() -> service.addAttributes(groupId, List.of("ATR000001"), NO_PERMISSION))
                .isInstanceOf(GroupChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.addAttributes("unknown", List.of("ATR000001"), CAN_CHANGE))
                .isInstanceOf(GroupNotFoundException.class)
                .hasMessage("No existe un grupo con el ID unknown.");
        assertThat(service.find(groupId).group().attributeIds()).isEmpty();
    }

    // --- User Story 3: remove an attribute ---

    @Test
    void removesAnAttributeWhichStaysInTheSystemAndInItsOtherGroups() {
        Attribute weight = createAttribute("Peso");
        String logistics = createGroup("Logísticos");
        String sales = createGroup("Compra Ventas");
        service.addAttributes(logistics, List.of("ATR000001"), CAN_CHANGE);
        service.addAttributes(sales, List.of("ATR000001"), CAN_CHANGE);

        service.removeAttribute(logistics, "atr000001", CAN_CHANGE);

        assertThat(service.find(logistics).group().attributeIds()).isEmpty();
        assertThat(service.find(sales).group().attributeIds()).containsExactly("ATR000001");
        assertThat(attributeService.find("ATR000001")).isEqualTo(weight);
    }

    @Test
    void removingTheLastGroupLeavesTheAttributeUngrouped() {
        createAttribute("Peso");
        String groupId = createGroup("Logísticos");
        service.addAttributes(groupId, List.of("ATR000001"), CAN_CHANGE);

        service.removeAttribute(groupId, "ATR000001", CAN_CHANGE);

        assertThat(service.findAllContaining("ATR000001")).isEmpty();
        assertThat(attributeService.find("ATR000001")).isNotNull();
    }

    @Test
    void refusesToRemoveANonMemberWithoutPermissionOrFromAnUnknownGroup() {
        createAttribute("Peso");
        String groupId = createGroup("Logísticos");

        assertThatThrownBy(() -> service.removeAttribute(groupId, "ATR000001", CAN_CHANGE))
                .isInstanceOf(AttributeNotInGroupException.class)
                .hasMessage("El atributo ATR000001 no está en este grupo.");
        assertThatThrownBy(() -> service.removeAttribute(groupId, "ATR000001", NO_PERMISSION))
                .isInstanceOf(GroupChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.removeAttribute("unknown", "ATR000001", CAN_CHANGE))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // --- User Story 4: view ---

    @Test
    void findsAGroupWithItsMembersInTheOrderAdded() {
        createAttribute("Volumen");
        createAttribute("Peso");
        String groupId = createGroup("Logísticos");
        service.addAttributes(groupId, List.of("ATR000002"), CAN_CHANGE);
        service.addAttributes(groupId, List.of("ATR000001"), CAN_CHANGE);

        assertThat(service.find(groupId).members())
                .extracting(GroupMember::displayName)
                .containsExactly("Peso", "Volumen");
        assertThatThrownBy(() -> service.find("unknown")).isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void anUnknownAttributeIsInNoGroup() {
        createGroup("Logísticos");

        assertThat(service.findAllContaining("ATR999")).isEmpty();
    }

    // --- User Story 5: rename and delete ---

    @Test
    void renamesAndKeepsTheIdentifierAndMembers() {
        createAttribute("Peso");
        String groupId = createGroup("Logisticos");
        service.addAttributes(groupId, List.of("ATR000001"), CAN_CHANGE);

        GroupDetails renamed = service.update(groupId, "Logística", "Peso y volumen", CAN_CHANGE);

        assertThat(renamed.group().id()).isEqualTo(groupId);
        assertThat(renamed.group().name().value()).isEqualTo("Logística");
        assertThat(renamed.group().description()).contains("Peso y volumen");
        assertThat(renamed.group().attributeIds()).containsExactly("ATR000001");
    }

    @Test
    void renamingToItsOwnNameInAnotherCaseIsAllowed() {
        String groupId = createGroup("Logísticos");

        assertThat(service.update(groupId, "LOGÍSTICOS", null, CAN_CHANGE)
                        .group()
                        .name()
                        .value())
                .isEqualTo("LOGÍSTICOS");
    }

    @Test
    void refusesToRenameToAnotherGroupsName() {
        createGroup("Compra Ventas");
        String groupId = createGroup("Logísticos");

        assertThatThrownBy(() -> service.update(groupId, " compra ventas ", null, CAN_CHANGE))
                .isInstanceOf(GroupNameInUseException.class);
        assertThat(service.find(groupId).group().name().value()).isEqualTo("Logísticos");
    }

    @Test
    void refusesToDeleteAGroupThatStillHasAttributes() {
        createAttribute("Peso");
        createAttribute("Volumen");
        String groupId = createGroup("Logísticos");
        service.addAttributes(groupId, List.of("ATR000001", "ATR000002"), CAN_CHANGE);

        assertThatThrownBy(() -> service.delete(groupId, CAN_CHANGE))
                .isInstanceOf(GroupNotEmptyException.class)
                .hasMessage("El grupo \"Logísticos\" tiene 2 atributos. Quítalos antes de eliminarlo.");
        assertThat(service.find(groupId).group().attributeIds()).hasSize(2);
    }

    @Test
    void deletesAnEmptyGroup() {
        String groupId = createGroup("Logísticos");

        service.delete(groupId, CAN_CHANGE);

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void refusesToRenameOrDeleteWithoutThePermissionOrAnUnknownGroup() {
        String groupId = createGroup("Logísticos");

        assertThatThrownBy(() -> service.update(groupId, "Logística", null, NO_PERMISSION))
                .isInstanceOf(GroupChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.delete(groupId, NO_PERMISSION))
                .isInstanceOf(GroupChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.update("unknown", "Logística", null, CAN_CHANGE))
                .isInstanceOf(GroupNotFoundException.class);
        assertThatThrownBy(() -> service.delete("unknown", CAN_CHANGE)).isInstanceOf(GroupNotFoundException.class);
        assertThat(service.find(groupId).group().name().value()).isEqualTo("Logísticos");
    }

    private Attribute createAttribute(String name) {
        return attributeService.create(
                new AttributeDraft(null, name, null, AttributeDataType.BOOLEAN, null),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
    }

    private String createGroup(String name) {
        return service.create(name, null, CAN_CHANGE).group().id();
    }
}
