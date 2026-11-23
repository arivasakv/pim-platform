package com.pim.backend.lov.model;

/** The change would leave the list with more than 50,000 values (research R-08; HTTP 422). Nothing changed. */
public class ListTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListTooLargeException() {
        super(LovErrorCode.LIST_TOO_LARGE.message());
    }

    /** A bulk load (feature 013) would leave the named list with {@code values} values. */
    public ListTooLargeException(String listName, int values) {
        super(LovErrorCode.LIST_TOO_LARGE_IN_FILE.message(listName, String.format(java.util.Locale.US, "%,d", values)));
    }
}
