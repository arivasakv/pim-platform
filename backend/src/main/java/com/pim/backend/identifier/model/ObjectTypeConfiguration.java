package com.pim.backend.identifier.model;

import java.util.Objects;

/**
 * The configuration of one object type: its ID sequence (FR-006) and its key-field declaration (FR-013).
 *
 * @param objectType the type configured
 * @param sequence its automatic ID sequence
 * @param keyFields its key fields
 */
public record ObjectTypeConfiguration(
        ObjectType objectType, SequenceConfiguration sequence, KeyFieldDeclaration keyFields) {

    public ObjectTypeConfiguration {
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(keyFields, "keyFields");
    }

    /** A copy with another sequence. */
    public ObjectTypeConfiguration withSequence(SequenceConfiguration newSequence) {
        return new ObjectTypeConfiguration(objectType, newSequence, keyFields);
    }
}
