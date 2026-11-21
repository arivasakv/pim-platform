package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.lov.model.ListInUseException;
import com.pim.backend.lov.repository.ListOfValuesRepository;
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
 * Deleting a list while an attribute is being linked to it never leaves an attribute linked to a missing list
 * (SC-005; research R-04), and the two locks never deadlock. Real services on the local test database.
 */
@SpringBootTest
class ListDeletionRaceTest {

    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);
    private static final int ROUNDS = 50;

    private final ListOfValuesService lovService;
    private final AttributeService attributeService;
    private final ListOfValuesRepository lists;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    ListDeletionRaceTest(
            @Autowired ListOfValuesService lovService,
            @Autowired AttributeService attributeService,
            @Autowired ListOfValuesRepository lists,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.lovService = lovService;
        this.attributeService = attributeService;
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
    void neverLeavesAnAttributeLinkedToADeletedList() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String listId = lovService
                        .create("Lista " + round, null, CAN_CHANGE, NO_MANUAL_IDS)
                        .list()
                        .systemId();
                // Keeps "some list exists" true, so attribute creation reaches the "this list exists" check.
                lovService.create("Ancla " + round, null, CAN_CHANGE, NO_MANUAL_IDS);
                CountDownLatch start = new CountDownLatch(1);
                String attributeName = "Atributo " + round;
                Future<Boolean> deletion = executor.submit(() -> {
                    start.await();
                    try {
                        lovService.delete(listId, null, CAN_CHANGE);
                        return true;
                    } catch (ListInUseException exception) {
                        return false;
                    }
                });
                Future<Boolean> creation = executor.submit(() -> {
                    start.await();
                    try {
                        attributeService.create(
                                new AttributeDraft(
                                        null,
                                        attributeName,
                                        AttributeDataType.LIST_OF_VALUES,
                                        new ListOfValuesProperties(SelectionMode.SINGLE, listId)),
                                CAN_CHANGE,
                                NO_MANUAL_IDS);
                        return true;
                    } catch (ListOfValuesUnavailableException exception) {
                        return false;
                    }
                });
                start.countDown();
                boolean deleted = deletion.get(30, TimeUnit.SECONDS);
                boolean created = creation.get(30, TimeUnit.SECONDS);

                assertThat(deleted).as("round %d: exactly one side wins", round).isNotEqualTo(created);
                boolean listExists = lists.findById(listId).isPresent();
                boolean linked = !attributeService.findLinkedToList(listId).isEmpty();
                assertThat(listExists).as("round %d", round).isEqualTo(linked);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
