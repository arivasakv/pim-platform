package com.pim.backend.identifier.model;

/** The ID already belongs to a different object (FR-001, FR-009b). */
public class SystemIdInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SystemId systemId;

    public SystemIdInUseException(SystemId systemId) {
        super(IdentifierErrorCode.SYSTEM_ID_IN_USE.message(systemId.value()));
        this.systemId = systemId;
    }

    public SystemId systemId() {
        return systemId;
    }
}
