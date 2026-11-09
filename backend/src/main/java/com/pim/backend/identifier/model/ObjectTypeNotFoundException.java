package com.pim.backend.identifier.model;

/** The requested object type does not exist (unknown code in a path). */
public class ObjectTypeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedObjectType;

    public ObjectTypeNotFoundException(String requestedObjectType) {
        super("Unknown object type " + requestedObjectType);
        this.requestedObjectType = requestedObjectType;
    }

    public String requestedObjectType() {
        return requestedObjectType;
    }
}
