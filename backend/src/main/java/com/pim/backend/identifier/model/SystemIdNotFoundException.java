package com.pim.backend.identifier.model;

/** No object has the requested system ID. */
public class SystemIdNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedSystemId;

    public SystemIdNotFoundException(String requestedSystemId) {
        super("No object has the system ID " + requestedSystemId);
        this.requestedSystemId = requestedSystemId;
    }

    public String requestedSystemId() {
        return requestedSystemId;
    }
}
