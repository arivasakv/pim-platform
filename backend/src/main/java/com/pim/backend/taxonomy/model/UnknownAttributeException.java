package com.pim.backend.taxonomy.model;

/** An attribute to assign does not exist in feature 004 (spec FR-011; HTTP 422). Nothing was assigned. */
public class UnknownAttributeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownAttributeException(String attributeId) {
        super(TaxonomyErrorCode.ATTRIBUTE_NOT_FOUND.message(attributeId));
    }
}
