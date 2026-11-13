package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.repository.EmptyListOfValuesCatalog;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.model.ListNameInUseException;
import com.pim.backend.lov.model.ValueIdInUseException;
import com.pim.backend.lov.repository.InMemoryListOfValuesRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Simultaneous changes keep names unique and value IDs distinct (FR-003, FR-008; research R-12). */
class ListOfValuesServiceConcurrencyTest {

    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final ManualIdPermissions NO_MANUAL_IDS = ManualIdPermissions.fromCodes(null);

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);
    private final InMemoryListOfValuesRepository lists = new InMemoryListOfValuesRepository();
    private final ListOfValuesService service = newService();

    private ListOfValuesService newService() {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        IdentifierService identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        AttributeService attributeService = new AttributeService(
                new InMemoryAttributeRepository(),
                new EmptyListOfValuesCatalog(),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                clock);
        return new ListOfValuesService(
                lists, identifierService, attributeService, new ChangeRecorder(record -> {}, clock), clock);
    }

    @Test
    void twentySimultaneousCreationsWithTheSameNameMakeOneList() throws Exception {
        AtomicInteger refused = new AtomicInteger();

        runTogether(20, index -> {
            try {
                service.create(index % 2 == 0 ? "Color" : "COLOR", null, CAN_CHANGE, NO_MANUAL_IDS);
            } catch (ListNameInUseException exception) {
                refused.incrementAndGet();
            }
        });

        assertThat(lists.findAll()).hasSize(1);
        assertThat(refused).hasValue(19);
    }

    @Test
    void fiftySimultaneousGeneratedIdsAreDistinct() throws Exception {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);

        runTogether(50, index -> service.addValue("LOV000001", null, "Valor " + index, null, CAN_CHANGE));

        List<String> ids = lists.findById("LOV000001").orElseThrow().values().stream()
                .map(value -> value.id().value())
                .toList();
        assertThat(ids)
                .hasSize(50)
                .containsExactlyInAnyOrderElementsOf(
                        IntStream.rangeClosed(1, 50).mapToObj("%04d"::formatted).toList());
    }

    @Test
    void twentySimultaneousManualAdditionsOfOneIdKeepOneValue() throws Exception {
        service.create("Color", null, CAN_CHANGE, NO_MANUAL_IDS);
        AtomicInteger refused = new AtomicInteger();

        runTogether(20, index -> {
            try {
                service.addValue("LOV000001", "0001", "Azul", null, CAN_CHANGE);
            } catch (ValueIdInUseException exception) {
                refused.incrementAndGet();
            }
        });

        assertThat(lists.findById("LOV000001").orElseThrow().values()).hasSize(1);
        assertThat(refused).hasValue(19);
    }

    private static void runTogether(int count, IntConsumer task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    task.accept(taskIndex);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
