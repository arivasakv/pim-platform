package com.pim.backend.taxonomy.model;

/** The node has no filter of its own for the attribute (feature 012 FR-005; HTTP 404). */
public class FilterNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FilterNotFoundException(String nodeName, String attributeName) {
        super(TaxonomyErrorCode.FILTER_NOT_FOUND.message(nodeName, attributeName));
    }
}
