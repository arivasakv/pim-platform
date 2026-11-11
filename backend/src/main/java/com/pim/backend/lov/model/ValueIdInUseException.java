package com.pim.backend.lov.model;

/** A value with the same ID, ignoring letter case, is already in the list (FR-008; HTTP 409). */
public class ValueIdInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValueIdInUseException(ValueId id) {
        super(LovErrorCode.VALUE_ID_IN_USE.message(id.value()));
    }
}
