package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pim.backend.lov.model.ChangeOperation;
import com.pim.backend.lov.model.ChangeRecord;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Change records (FR-019; research R-11). */
class ChangeRecorderTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final ListOfValues COLOR = ListOfValues.create("LOV000001", ListName.of("Color"), NOW);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void recordsDeletionsWithTheTimeAndTheUser() {
        List<ChangeRecord> records = new ArrayList<>();
        ChangeRecorder recorder = new ChangeRecorder(records::add, clock);

        recorder.valueDeleted(COLOR, "0002", "ana");
        recorder.listDeleted(COLOR, null);

        assertThat(records)
                .extracting(ChangeRecord::operation, ChangeRecord::user, ChangeRecord::valueIds)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ChangeOperation.VALUE_DELETED, "ana", List.of("0002")),
                        org.assertj.core.groups.Tuple.tuple(ChangeOperation.LIST_DELETED, null, List.of()));
        assertThat(records).allSatisfy(record -> {
            assertThat(record.at()).isEqualTo(NOW);
            assertThat(record.listId()).isEqualTo("LOV000001");
            assertThat(record.listName()).isEqualTo("Color");
            assertThat(record.id()).isNotBlank();
        });
    }

    @Test
    void aStorageFailureIsLoggedAndNotRethrown() {
        ChangeRecorder recorder = new ChangeRecorder(
                record -> {
                    throw new IllegalStateException("down");
                },
                clock);

        assertThatCode(() -> recorder.valuesImported(COLOR, 1, 2, "ana")).doesNotThrowAnyException();
    }
}
