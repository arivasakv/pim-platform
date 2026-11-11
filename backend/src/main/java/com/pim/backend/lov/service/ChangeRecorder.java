package com.pim.backend.lov.service;

import com.pim.backend.lov.model.ChangeOperation;
import com.pim.backend.lov.model.ChangeRecord;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.repository.ChangeRecordRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps who deleted or imported list values, when, and what (FR-019; research R-11). Records are written after the
 * change succeeded; a failure to write one is logged and does not undo the change.
 */
@Component
public class ChangeRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeRecorder.class);

    private final ChangeRecordRepository records;
    private final Clock clock;

    public ChangeRecorder(ChangeRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    public void valueDeleted(ListOfValues list, String valueId, String user) {
        record(user, ChangeOperation.VALUE_DELETED, list, List.of(valueId), 0, 0);
    }

    public void listDeleted(ListOfValues list, String user) {
        record(user, ChangeOperation.LIST_DELETED, list, List.of(), 0, 0);
    }

    public void valuesImported(ListOfValues list, int added, int updated, String user) {
        record(user, ChangeOperation.VALUES_IMPORTED, list, List.of(), added, updated);
    }

    private void record(
            String user, ChangeOperation operation, ListOfValues list, List<String> valueIds, int added, int updated) {
        ChangeRecord changeRecord = new ChangeRecord(
                UUID.randomUUID().toString(),
                clock.instant(),
                user,
                operation,
                list.systemId(),
                list.name().value(),
                valueIds,
                added,
                updated);
        try {
            records.insert(changeRecord);
        } catch (RuntimeException exception) {
            LOG.error("Could not store the change record {}", changeRecord, exception);
        }
    }
}
