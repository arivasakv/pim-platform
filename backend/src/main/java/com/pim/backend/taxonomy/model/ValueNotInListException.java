package com.pim.backend.taxonomy.model;

/** A filter names a value ID that its attribute's list does not have (feature 012 FR-004; HTTP 422). */
public class ValueNotInListException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValueNotInListException(String listName, String valueId) {
        super(TaxonomyErrorCode.VALUE_NOT_IN_LIST.message(listName, valueId));
    }
}
