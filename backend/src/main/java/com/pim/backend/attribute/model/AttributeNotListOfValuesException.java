package com.pim.backend.attribute.model;

/** The attribute has no list of values to unlink (feature 014 FR-012; HTTP 422). Nothing changed. */
public class AttributeNotListOfValuesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeNotListOfValuesException(String displayName) {
        super(AttributeErrorCode.ATTRIBUTE_NOT_LIST_OF_VALUES.message(displayName));
    }
}
