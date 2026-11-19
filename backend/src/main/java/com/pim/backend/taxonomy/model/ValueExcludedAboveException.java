package com.pim.backend.taxonomy.model;

/**
 * A filter may only include values allowed by the filters above its node (feature 012 FR-009, ERF «Sobreescritura
 * Restrictiva»; HTTP 409). Nothing changed.
 */
public class ValueExcludedAboveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValueExcludedAboveException(ListValueOption value, NodeReference excludedBy) {
        super(TaxonomyErrorCode.VALUE_EXCLUDED_ABOVE.message(value.label(), value.id(), excludedBy.name()));
    }
}
