package com.pim.backend.taxonomy.model;

/** A filter with no value or more than 50,000 (feature 012 FR-002; HTTP 400). Nothing changed. */
public class InvalidFilterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidFilterException() {
        super(TaxonomyErrorCode.INVALID_FILTER_VALUES.message());
    }
}
