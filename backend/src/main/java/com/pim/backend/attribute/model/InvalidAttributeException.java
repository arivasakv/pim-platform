package com.pim.backend.attribute.model;

/** A request value breaks an attribute rule (HTTP 400). Carries the request field and the error code. */
public class InvalidAttributeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final AttributeErrorCode code;

    public InvalidAttributeException(String field, AttributeErrorCode code, Object... messageArguments) {
        super(code.message(messageArguments));
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public AttributeErrorCode code() {
        return code;
    }
}
