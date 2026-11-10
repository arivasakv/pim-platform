package com.pim.backend.attributegroup.model;

/** A request value breaks a group rule (HTTP 400). Carries the request field and the error code. */
public class InvalidGroupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final AttributeGroupErrorCode code;

    public InvalidGroupException(String field, AttributeGroupErrorCode code) {
        super(code.message());
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public AttributeGroupErrorCode code() {
        return code;
    }
}
