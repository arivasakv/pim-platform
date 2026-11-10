package com.pim.backend.attributegroup.model;

/** The attribute to remove is not a member of the group (plan decision D-7; HTTP 404). */
public class AttributeNotInGroupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeNotInGroupException(String attributeId) {
        super(AttributeGroupErrorCode.ATTRIBUTE_NOT_IN_GROUP.message(attributeId));
    }
}
