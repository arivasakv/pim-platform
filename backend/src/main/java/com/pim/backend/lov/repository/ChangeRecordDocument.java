package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ChangeRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A change record as stored in the collection {@value #COLLECTION} (data-model "Collections"). Package-private
 * (constitution XII).
 */
@Document(ChangeRecordDocument.COLLECTION)
record ChangeRecordDocument(
        @Id String id,
        Instant at,
        String user,
        String operation,
        String listId,
        String listName,
        List<String> valueIds,
        int added,
        int updated) {

    static final String COLLECTION = "lov_changes";
    static final String INDEX = "lov_changes_list_at";

    static ChangeRecordDocument from(ChangeRecord record) {
        return new ChangeRecordDocument(
                record.id(),
                record.at(),
                record.user(),
                record.operation().name(),
                record.listId(),
                record.listName(),
                record.valueIds(),
                record.added(),
                record.updated());
    }
}
