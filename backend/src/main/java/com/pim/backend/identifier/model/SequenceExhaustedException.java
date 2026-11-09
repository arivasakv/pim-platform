package com.pim.backend.identifier.model;

/** The object type's sequence has no free value left (plan decision D-2). */
public class SequenceExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ObjectType objectType;

    public SequenceExhaustedException(ObjectType objectType) {
        super(IdentifierErrorCode.SEQUENCE_EXHAUSTED.message(objectType.name()));
        this.objectType = objectType;
    }

    public ObjectType objectType() {
        return objectType;
    }
}
