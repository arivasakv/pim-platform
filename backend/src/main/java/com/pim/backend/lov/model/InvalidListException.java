package com.pim.backend.lov.model;

/** A request value breaks a list or value rule (HTTP 400). Carries the request field and the error code. */
public class InvalidListException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final LovErrorCode code;

    public InvalidListException(String field, LovErrorCode code) {
        super(code.message());
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public LovErrorCode code() {
        return code;
    }
}
