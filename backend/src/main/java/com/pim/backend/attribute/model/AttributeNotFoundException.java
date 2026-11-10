package com.pim.backend.attribute.model;

/** No attribute has the requested system ID (HTTP 404). */
public class AttributeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedSystemId;

    public AttributeNotFoundException(String requestedSystemId) {
        super("No attribute has the system ID " + requestedSystemId);
        this.requestedSystemId = requestedSystemId;
    }

    public String requestedSystemId() {
        return requestedSystemId;
    }
}
