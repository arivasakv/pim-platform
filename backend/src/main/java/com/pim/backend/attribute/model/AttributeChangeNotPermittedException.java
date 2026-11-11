package com.pim.backend.attribute.model;

/**
 * The user may not edit or delete attributes: the existing {@code ATTRIBUTE_CREATE} check failed (feature 007
 * FR-018, research R-09; HTTP 403).
 */
public class AttributeChangeNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeChangeNotPermittedException() {
        super(AttributeErrorCode.ATTRIBUTE_CHANGE_NOT_PERMITTED.message());
    }
}
