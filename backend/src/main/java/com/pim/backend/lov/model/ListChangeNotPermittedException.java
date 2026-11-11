package com.pim.backend.lov.model;

/** The user lacks the {@code ATTRIBUTE_CREATE} permission needed to change lists (FR-002; HTTP 403). */
public class ListChangeNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListChangeNotPermittedException() {
        super(LovErrorCode.ATTRIBUTE_CREATE_NOT_PERMITTED.message());
    }
}
