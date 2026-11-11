package com.pim.backend.lov.model;

/** Another list already has the name, ignoring letter case (FR-003; HTTP 409). */
public class ListNameInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListNameInUseException(ListName name) {
        super(LovErrorCode.LIST_NAME_IN_USE.message(name.value()));
    }
}
