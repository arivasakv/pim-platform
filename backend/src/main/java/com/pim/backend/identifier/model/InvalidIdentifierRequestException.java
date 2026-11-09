package com.pim.backend.identifier.model;

/** A request value breaks an identifier rule (HTTP 400). Carries the request field and the error code. */
public class InvalidIdentifierRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final IdentifierErrorCode code;

    public InvalidIdentifierRequestException(String field, IdentifierErrorCode code, Object... messageArguments) {
        super(code.message(messageArguments));
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public IdentifierErrorCode code() {
        return code;
    }
}
