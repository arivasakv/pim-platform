package com.pim.backend.attribute.model;

/**
 * An edit names another system ID or data type, which never change (feature 007 FR-010; HTTP 422). Carries the
 * request field and the error code.
 */
public class ImmutableFieldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final AttributeErrorCode code;

    private ImmutableFieldException(String field, AttributeErrorCode code) {
        super(code.message());
        this.field = field;
        this.code = code;
    }

    public static ImmutableFieldException systemId() {
        return new ImmutableFieldException("systemId", AttributeErrorCode.SYSTEM_ID_IMMUTABLE);
    }

    public static ImmutableFieldException dataType() {
        return new ImmutableFieldException("dataType", AttributeErrorCode.DATA_TYPE_IMMUTABLE);
    }

    public String field() {
        return field;
    }

    public AttributeErrorCode code() {
        return code;
    }
}
