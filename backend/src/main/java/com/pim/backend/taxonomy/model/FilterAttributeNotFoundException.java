package com.pim.backend.taxonomy.model;

/**
 * The attribute named in a filter request does not exist (feature 012 FR-003; HTTP 404, field {@code attributeId}).
 * Assignments use {@link UnknownAttributeException} (422, field {@code attributeIds}) instead, as feature 010's
 * contract says.
 */
public class FilterAttributeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FilterAttributeNotFoundException(String attributeId) {
        super(TaxonomyErrorCode.ATTRIBUTE_NOT_FOUND.message(attributeId));
    }
}
