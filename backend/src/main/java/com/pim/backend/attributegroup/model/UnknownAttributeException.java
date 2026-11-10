package com.pim.backend.attributegroup.model;

/** An attribute to add does not exist in feature 004 (FR-011; HTTP 422). Nothing was added. */
public class UnknownAttributeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String attributeId;

    public UnknownAttributeException(String attributeId) {
        super(AttributeGroupErrorCode.ATTRIBUTE_NOT_FOUND.message(attributeId));
        this.attributeId = attributeId;
    }

    public String attributeId() {
        return attributeId;
    }
}
