package com.pim.backend.identifier.model;

/** A manual ID was given by a user without the manual-ID permission for that object type (FR-003). */
public class ManualIdNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ObjectType objectType;
    private final String requestedSystemId;

    public ManualIdNotPermittedException(ObjectType objectType, String requestedSystemId) {
        super(IdentifierErrorCode.MANUAL_ID_NOT_PERMITTED.message(requestedSystemId, objectType.name()));
        this.objectType = objectType;
        this.requestedSystemId = requestedSystemId;
    }

    public ObjectType objectType() {
        return objectType;
    }

    public String requestedSystemId() {
        return requestedSystemId;
    }
}
