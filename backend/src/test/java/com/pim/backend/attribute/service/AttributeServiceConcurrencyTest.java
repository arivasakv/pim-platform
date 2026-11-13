package com.pim.backend.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.DisplayNameInUseException;
import com.pim.backend.attribute.repository.EmptyListOfValuesCatalog;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** "Check display name, assign ID, save" is atomic under concurrent requests (research R-18; FR-023, SC-002). */
class AttributeServiceConcurrencyTest {

    private static final AttributePermissions CAN_CREATE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);

    private final InMemorySystemIdRegistry systemIds = new InMemorySystemIdRegistry();
    private final InMemoryObjectTypeConfigurationRepository objectTypes =
            new InMemoryObjectTypeConfigurationRepository();
    private final InMemoryAttributeRepository attributes = new InMemoryAttributeRepository();
    private final AttributeService service = newService();
    private final ExecutorService executor = Executors.newFixedThreadPool(32);

    private AttributeService newService() {
        new DefaultObjectTypeConfigurations(objectTypes).afterPropertiesSet();
        IdentifierService identifierService = new IdentifierService(systemIds, objectTypes, Clock.systemUTC());
        return new AttributeService(
                attributes,
                new EmptyListOfValuesCatalog(),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                Clock.systemUTC());
    }

    @AfterEach
    void stopTheThreads() {
        executor.shutdownNow();
    }

    @Test
    void ofTwentySimultaneousCreationsWithTheSameNameOnlyOneSucceeds() throws Exception {
        List<String> outcomes = runAtOnce(20, index -> {
            try {
                return service.create(draft("Color"), CAN_CREATE, NO_MANUAL_IDS).systemId();
            } catch (DisplayNameInUseException exception) {
                return "IN_USE";
            }
        });

        assertThat(outcomes).filteredOn("IN_USE"::equals).hasSize(19);
        assertThat(attributes.count()).isEqualTo(1);
        assertThat(systemIds.count()).isEqualTo(1);
    }

    @Test
    void oneHundredSimultaneousCreationsWithDistinctNamesGetDistinctIds() throws Exception {
        List<String> ids = runAtOnce(
                100,
                index -> service.create(draft("Atributo " + index), CAN_CREATE, NO_MANUAL_IDS)
                        .systemId());

        assertThat(ids).hasSize(100).doesNotHaveDuplicates();
        assertThat(attributes.count()).isEqualTo(100);
    }

    private static AttributeDraft draft(String name) {
        return new AttributeDraft(null, name, null, AttributeDataType.BOOLEAN, null);
    }

    /** Runs {@code count} tasks released at the same moment and returns their results. */
    private List<String> runAtOnce(int count, IndexedTask task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            Callable<String> callable = () -> {
                start.await();
                return task.run(index);
            };
            futures.add(executor.submit(callable));
        }
        start.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    @FunctionalInterface
    private interface IndexedTask {
        String run(int index);
    }
}
