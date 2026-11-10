package com.pim.backend.attributegroup.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.repository.EmptyListOfValuesCatalog;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.attributegroup.model.GroupNameInUseException;
import com.pim.backend.attributegroup.repository.InMemoryAttributeGroupRepository;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Simultaneous group changes keep names unique and members once each (FR-003, FR-008; research R-12). */
class AttributeGroupServiceConcurrencyTest {

    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T18:00:00Z"), ZoneOffset.UTC);
    private final AttributeService attributeService = newAttributeService();
    private final InMemoryAttributeGroupRepository groups = new InMemoryAttributeGroupRepository();
    private final AttributeGroupService service = new AttributeGroupService(groups, attributeService, clock);

    private AttributeService newAttributeService() {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        IdentifierService identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        return new AttributeService(
                new InMemoryAttributeRepository(), new EmptyListOfValuesCatalog(), identifierService, clock);
    }

    @Test
    void twentySimultaneousCreationsWithTheSameNameMakeOneGroup() throws Exception {
        AtomicInteger refused = new AtomicInteger();

        runTogether(20, index -> {
            try {
                service.create(index % 2 == 0 ? "Logísticos" : "LOGÍSTICOS", null, CAN_CHANGE);
            } catch (GroupNameInUseException exception) {
                refused.incrementAndGet();
            }
        });

        assertThat(service.findAll()).hasSize(1);
        assertThat(refused).hasValue(19);
    }

    @Test
    void fiftySimultaneousAdditionsLeaveEachAttributeOnce() throws Exception {
        List<String> ids = IntStream.rangeClosed(1, 50)
                .mapToObj(number -> attributeService
                        .create(
                                new AttributeDraft(null, "Atributo " + number, null, AttributeDataType.BOOLEAN, null),
                                CAN_CHANGE,
                                ManualIdPermissions.fromCodes(null))
                        .systemId())
                .toList();
        String groupId = service.create("Logísticos", null, CAN_CHANGE).group().id();

        runTogether(50, index -> service.addAttributes(groupId, List.of(ids.get(index)), CAN_CHANGE));

        assertThat(service.find(groupId).group().attributeIds()).containsExactlyInAnyOrderElementsOf(ids);
    }

    private static void runTogether(int tasks, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Void>> futures = new ArrayList<>();
            for (int index = 0; index < tasks; index++) {
                int current = index;
                Callable<Void> callable = () -> {
                    start.await();
                    task.run(current);
                    return null;
                };
                futures.add(executor.submit(callable));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask {
        void run(int index);
    }
}
