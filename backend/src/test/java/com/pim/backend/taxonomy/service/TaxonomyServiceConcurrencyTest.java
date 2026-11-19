package com.pim.backend.taxonomy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
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
import com.pim.backend.taxonomy.model.NodeMoveNotAllowedException;
import com.pim.backend.taxonomy.model.NodeNameInUseException;
import com.pim.backend.taxonomy.model.TaxonomyErrorCode;
import com.pim.backend.taxonomy.repository.InMemoryTaxonomyNodeRepository;
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

/** Simultaneous taxonomy changes keep sibling names unique and assignments once each (spec edge case "concurrent"). */
class TaxonomyServiceConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-11-12T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AttributeService attributeService;
    private final IdentifierService identifierService;
    private final InMemoryTaxonomyNodeRepository nodes = new InMemoryTaxonomyNodeRepository();
    private final TaxonomyService service;

    {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        attributeService = new AttributeService(
                new InMemoryAttributeRepository(),
                new EmptyListOfValuesCatalog(),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                clock);
        service = new TaxonomyService(nodes, attributeService, identifierService, clock);
    }

    private Attribute createAttribute(String name) {
        return attributeService.create(
                new AttributeDraft(null, name, null, AttributeDataType.BOOLEAN, null),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
    }

    @Test
    void twentySimultaneousCreationsWithTheSameNameUnderOneParentMakeOneNode() throws Exception {
        String ropa = service.create("Ropa", null, null, CAN_CHANGE).node().systemId();
        AtomicInteger refused = new AtomicInteger();

        runTogether(20, index -> {
            try {
                service.create(index % 2 == 0 ? "Camisas" : "CAMISAS", null, ropa, CAN_CHANGE);
            } catch (NodeNameInUseException exception) {
                refused.incrementAndGet();
            }
        });

        assertThat(service.find(ropa).children()).hasSize(1);
        assertThat(refused).hasValue(19);
    }

    @Test
    void fiftySimultaneousAssignmentsLeaveEachAttributeOnce() throws Exception {
        List<String> ids = IntStream.rangeClosed(1, 50)
                .mapToObj(number -> createAttribute("Atributo " + number).systemId())
                .toList();
        String ropa = service.create("Ropa", null, null, CAN_CHANGE).node().systemId();

        runTogether(50, index -> service.addAttributes(ropa, List.of(ids.get(index), ids.get(0)), CAN_CHANGE));

        assertThat(service.find(ropa).node().attributeIds()).containsExactlyInAnyOrderElementsOf(ids);
    }

    /** Feature 011 SC-003: "A under B" and "B under A" at the same time never make a loop. */
    @Test
    void crossingSimultaneousMovesNeverMakeALoop() throws Exception {
        for (int round = 1; round <= 25; round++) {
            String a =
                    service.create("A" + round, null, null, CAN_CHANGE).node().systemId();
            String b =
                    service.create("B" + round, null, null, CAN_CHANGE).node().systemId();
            AtomicInteger refused = new AtomicInteger();

            runTogether(2, index -> {
                try {
                    if (index == 0) {
                        service.move(a, b, CAN_CHANGE);
                    } else {
                        service.move(b, a, CAN_CHANGE);
                    }
                } catch (NodeMoveNotAllowedException exception) {
                    assertThat(exception.code()).isEqualTo(TaxonomyErrorCode.NODE_MOVE_INTO_OWN_BRANCH);
                    refused.incrementAndGet();
                }
            });

            assertThat(refused).as("round %d", round).hasValue(1);
            int levels = service.find(a).level() + service.find(b).level();
            assertThat(levels)
                    .as("round %d: one top-level node and one child", round)
                    .isEqualTo(3);
        }
    }

    private interface IndexedTask {
        void run(int index) throws Exception;
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
}
