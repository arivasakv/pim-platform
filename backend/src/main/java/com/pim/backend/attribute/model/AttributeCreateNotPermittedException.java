package com.pim.backend.attribute.model;

/** The user may not create attributes (FR-002, FR-019; HTTP 403). */
public class AttributeCreateNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeCreateNotPermittedException() {
        super(AttributeErrorCode.ATTRIBUTE_CREATE_NOT_PERMITTED.message());
    }
}
