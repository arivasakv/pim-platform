package com.pim.backend.identifier.model;

/** Someone tried to change a system ID; a saved ID never changes (FR-007). */
public class SystemIdImmutableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedSystemId;

    public SystemIdImmutableException(String requestedSystemId) {
        super("The system ID " + requestedSystemId + " cannot be changed");
        this.requestedSystemId = requestedSystemId;
    }

    public String requestedSystemId() {
        return requestedSystemId;
    }
}
