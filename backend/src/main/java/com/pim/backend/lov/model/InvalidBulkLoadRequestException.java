package com.pim.backend.lov.model;

/**
 * A bulk load request is missing a part or has an unknown value (feature 013: {@code CHECK_TOKEN_REQUIRED},
 * {@code INVALID_FORMAT}; HTTP 400). Nothing was saved.
 */
public class InvalidBulkLoadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final LovErrorCode code;

    public InvalidBulkLoadRequestException(String field, LovErrorCode code) {
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
