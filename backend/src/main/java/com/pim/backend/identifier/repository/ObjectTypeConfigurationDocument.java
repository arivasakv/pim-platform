package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceKind;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The configuration of one object type as stored in the collection {@value #COLLECTION} (ADR 0003). Package-private
 * (constitution XII).
 *
 * @param id the object-type code, e.g. {@code ATTRIBUTE}
 * @param prefix {@code null} for numeric sequences
 */
@Document(ObjectTypeConfigurationDocument.COLLECTION)
record ObjectTypeConfigurationDocument(
        @Id String id, SequenceKind kind, String prefix, int minimumDigits, long nextValue, List<String> keyFields) {

    static final String COLLECTION = "object_type_configurations";

    static ObjectTypeConfigurationDocument from(ObjectTypeConfiguration configuration) {
        SequenceConfiguration sequence = configuration.sequence();
        return new ObjectTypeConfigurationDocument(
                configuration.objectType().name(),
                sequence.kind(),
                sequence.prefix(),
                sequence.minimumDigits(),
                sequence.nextValue(),
                configuration.keyFields().fieldNames());
    }

    ObjectType objectType() {
        return ObjectType.valueOf(id);
    }

    ObjectTypeConfiguration toConfiguration() {
        return new ObjectTypeConfiguration(
                objectType(),
                new SequenceConfiguration(kind, prefix, minimumDigits, nextValue),
                new KeyFieldDeclaration(keyFields == null ? List.of() : keyFields));
    }
}
