package com.pim.backend.identifier.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.AssignmentOutcome;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** No duplicate system IDs under concurrent creation and at volume (SC-002; research R-10, plan decision D-9). */
class IdentifierServiceConcurrencyTest {

    private static final ManualIdPermissions NO_PERMISSIONS = ManualIdPermissions.fromCodes(null);

    private final InMemorySystemIdRegistry registry = new InMemorySystemIdRegistry();
    private final InMemoryObjectTypeConfigurationRepository configurations =
            new InMemoryObjectTypeConfigurationRepository();
    private final IdentifierService service = newService();
    private final ExecutorService executor = Executors.newFixedThreadPool(32);

    private IdentifierService newService() {
        new DefaultObjectTypeConfigurations(configurations).afterPropertiesSet();
        return new IdentifierService(registry, configurations, Clock.systemUTC());
    }

    @AfterEach
    void stopTheThreads() {
        executor.shutdownNow();
    }

    @Test
    void oneHundredSimultaneousAssignmentsGetOneHundredDistinctIds() throws Exception {
        List<String> ids = runAtOnce(100, index -> assignAutomatic(ObjectType.PRODUCT));

        Set<String> expected = new HashSet<>();
        IntStream.rangeClosed(1, 100).forEach(i -> expected.add("PRD%06d".formatted(i)));
        assertThat(ids).hasSize(100).doesNotHaveDuplicates();
        assertThat(new HashSet<>(ids)).isEqualTo(expected);
    }

    @Test
    void oneThousandAssignmentsAcrossAllTypesShareNoId() throws Exception {
        ObjectType[] types = ObjectType.values();

        List<String> ids = runAtOnce(1_000, index -> assignAutomatic(types[index % types.length]));

        assertThat(ids).hasSize(1_000).doesNotHaveDuplicates();
        assertThat(registry.count()).isEqualTo(1_000);
    }

    @Test
    void ofTwoSimultaneousIdenticalManualIdsOnlyOneIsRegistered() throws Exception {
        ManualIdPermissions classification = ManualIdPermissions.fromCodes("CLASSIFICATION");

        List<String> outcomes = runAtOnce(2, index -> {
            try {
                AssignmentOutcome outcome = service.assign(ObjectType.CLASSIFICATION, "CLS-X", Map.of(), classification)
                        .outcome();
                return outcome.name();
            } catch (SystemIdInUseException exception) {
                return "IN_USE";
            }
        });

        assertThat(outcomes).containsExactlyInAnyOrder("ASSIGNED", "IN_USE");
        assertThat(registry.count()).isEqualTo(1);
    }

    private String assignAutomatic(ObjectType type) {
        return service.assign(type, null, Map.of(), NO_PERMISSIONS)
                .registration()
                .systemId()
                .value();
    }

    /** Runs {@code count} tasks released at the same moment and returns their results. */
    private List<String> runAtOnce(int count, IndexedTask task) throws InterruptedException, ExecutionException {
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
