package com.pim.backend.lov.model;

/** The change would leave the list with more than 50,000 values (research R-08; HTTP 422). Nothing changed. */
public class ListTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListTooLargeException() {
        super(LovErrorCode.LIST_TOO_LARGE.message());
    }
}
