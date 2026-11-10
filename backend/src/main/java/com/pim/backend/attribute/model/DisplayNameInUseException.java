package com.pim.backend.attribute.model;

/** Another attribute already has this display name, ignoring letter case (FR-023; HTTP 409). */
public class DisplayNameInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DisplayNameInUseException(DisplayName displayName) {
        super(AttributeErrorCode.DISPLAY_NAME_IN_USE.message(displayName.value()));
    }
}
