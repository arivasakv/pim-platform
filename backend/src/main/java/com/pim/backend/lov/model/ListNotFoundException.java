package com.pim.backend.lov.model;

/** No list of values has this system ID (HTTP 404). */
public class ListNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListNotFoundException(String listId) {
        super(LovErrorCode.LIST_NOT_FOUND.message(listId));
    }
}
