package com.pim.backend.taxonomy.model;

/** A request value breaks a node rule (HTTP 400). Carries the request field and the error code. */
public class InvalidNodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final TaxonomyErrorCode code;

    public InvalidNodeException(String field, TaxonomyErrorCode code) {
        super(code.message());
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public TaxonomyErrorCode code() {
        return code;
    }
}
