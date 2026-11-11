package com.pim.backend.lov.model;

/** The list has no value with this ID (HTTP 404). */
public class ValueNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValueNotFoundException(String valueId) {
        super(LovErrorCode.VALUE_NOT_FOUND.message(valueId));
    }
}
