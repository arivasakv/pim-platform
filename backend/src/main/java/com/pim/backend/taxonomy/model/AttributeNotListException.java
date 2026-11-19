package com.pim.backend.taxonomy.model;

/** Only list-of-values attributes can be filtered (feature 012 FR-003; HTTP 422). Nothing changed. */
public class AttributeNotListException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeNotListException(String attributeName) {
        super(TaxonomyErrorCode.ATTRIBUTE_NOT_LIST.message(attributeName));
    }
}
