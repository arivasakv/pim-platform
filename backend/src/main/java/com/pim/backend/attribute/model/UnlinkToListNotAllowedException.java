package com.pim.backend.attribute.model;

/** The new data type of an unlinked attribute cannot be list of values (feature 014 FR-002; HTTP 422). */
public class UnlinkToListNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnlinkToListNotAllowedException() {
        super(AttributeErrorCode.UNLINK_TO_LIST_NOT_ALLOWED.message());
    }
}
