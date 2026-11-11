package com.pim.backend.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeInGroupsException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attributegroup.model.UnknownAttributeException;
import com.pim.backend.attributegroup.service.AttributeGroupService;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.lov.model.ListInUseException;
import com.pim.backend.lov.repository.ListOfValuesRepository;
import com.pim.backend.lov.service.ListOfValuesService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Edits and deletions never interleave with group membership changes or list deletions in a way that leaves a group
 * pointing at a missing attribute or an attribute linked to a missing list (feature 007 FR-017, SC-005; research
 * R-06), and the locks never deadlock. Real services on the local test database.
 */
@SpringBootTest
class AttributeChangeRaceTest {

    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);
    private static final int ROUNDS = 40;

    private final AttributeService attributeService;
    private final AttributeGroupService groupService;
    private final ListOfValuesService lovService;
    private final ListOfValuesRepository lists;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    AttributeChangeRaceTest(
            @Autowired AttributeService attributeService,
            @Autowired AttributeGroupService groupService,
            @Autowired ListOfValuesService lovService,
            @Autowired ListOfValuesRepository lists,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.attributeService = attributeService;
        this.groupService = groupService;
        this.lovService = lovService;
        this.lists = lists;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    @BeforeEach
    void startFromAFreshInstall() {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void neverLeavesAGroupPointingAtADeletedAttribute() throws Exception {
        String groupId =
                groupService.create("Logísticos", null, CAN_CHANGE).group().id();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String attributeId = attributeService
                        .create(
                                new AttributeDraft(null, "Atributo " + round, null, AttributeDataType.BOOLEAN, null),
                                CAN_CHANGE,
                                NO_MANUAL_IDS)
                        .systemId();
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> deletion = executor.submit(() -> {
                    start.await();
                    try {
                        attributeService.delete(attributeId, CAN_CHANGE);
                        return true;
                    } catch (AttributeInGroupsException exception) {
                        return false;
                    }
                });
                Future<Boolean> addition = executor.submit(() -> {
                    start.await();
                    try {
                        groupService.addAttributes(groupId, List.of(attributeId), CAN_CHANGE);
                        return true;
                    } catch (UnknownAttributeException exception) {
                        return false;
                    }
                });
                start.countDown();
                boolean deleted = deletion.get(30, TimeUnit.SECONDS);
                boolean added = addition.get(30, TimeUnit.SECONDS);

                assertThat(deleted).as("round %d: exactly one side wins", round).isNotEqualTo(added);
                // The group's detail looks every member up; it fails if a member is missing.
                assertThat(groupService.find(groupId).members())
                        .as("round %d", round)
                        .hasSize(added ? 1 : 0);
                if (added) {
                    groupService.removeAttribute(groupId, attributeId, CAN_CHANGE);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void neverLeavesAnEditedAttributeLinkedToADeletedList() throws Exception {
        String anchor = lovService
                .create("Ancla", null, CAN_CHANGE, NO_MANUAL_IDS)
                .list()
                .systemId();
        Attribute attribute = attributeService.create(listDraft("Medida", anchor), CAN_CHANGE, NO_MANUAL_IDS);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String listId = lovService
                        .create("Lista " + round, null, CAN_CHANGE, NO_MANUAL_IDS)
                        .list()
                        .systemId();
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> deletion = executor.submit(() -> {
                    start.await();
                    try {
                        lovService.delete(listId, null, CAN_CHANGE);
                        return true;
                    } catch (ListInUseException exception) {
                        return false;
                    }
                });
                Future<Boolean> relink = executor.submit(() -> {
                    start.await();
                    try {
                        attributeService.update(attribute.systemId(), listDraft("Medida", listId), CAN_CHANGE);
                        return true;
                    } catch (ListOfValuesUnavailableException exception) {
                        return false;
                    }
                });
                start.countDown();
                boolean deleted = deletion.get(30, TimeUnit.SECONDS);
                boolean relinked = relink.get(30, TimeUnit.SECONDS);

                assertThat(deleted).as("round %d: exactly one side wins", round).isNotEqualTo(relinked);
                ListOfValuesProperties properties = (ListOfValuesProperties)
                        attributeService.find(attribute.systemId()).properties();
                assertThat(lists.findById(properties.listOfValuesId()))
                        .as("round %d: the linked list exists", round)
                        .isPresent();
                if (relinked) {
                    // Back to the anchor list, so the next round's list can be deleted.
                    attributeService.update(attribute.systemId(), listDraft("Medida", anchor), CAN_CHANGE);
                    lovService.delete(listId, null, CAN_CHANGE);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static AttributeDraft listDraft(String name, String listId) {
        return new AttributeDraft(
                null,
                name,
                null,
                AttributeDataType.LIST_OF_VALUES,
                new ListOfValuesProperties(SelectionMode.SINGLE, listId));
    }
}
