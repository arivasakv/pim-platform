package com.pim.backend.lov.model;

/** The list cannot be deleted because attributes use it (FR-004; HTTP 409). Nothing was deleted. */
public class ListInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ListInUseException(String listName, int attributeCount) {
        super(LovErrorCode.LIST_IN_USE.message(
                listName, attributeCount == 1 ? "1 atributo" : attributeCount + " atributos"));
    }
}
