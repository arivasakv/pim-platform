package com.pim.backend.attribute.model;

/**
 * The attribute or its list-value filters changed since the unlink was reviewed (feature 014 FR-011, research R-02;
 * HTTP 409). Nothing changed.
 */
public class UnlinkOutdatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnlinkOutdatedException() {
        super(AttributeErrorCode.UNLINK_OUTDATED.message());
    }
}
