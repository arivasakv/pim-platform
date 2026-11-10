package com.pim.backend.attribute.model;

/**
 * A list-of-values attribute cannot be created: no lists exist yet, or the chosen list does not exist (FR-012;
 * HTTP 422).
 */
public class ListOfValuesUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AttributeErrorCode code;

    private ListOfValuesUnavailableException(AttributeErrorCode code, Object... messageArguments) {
        super(code.message(messageArguments));
        this.code = code;
    }

    /** No list of values exists at all. */
    public static ListOfValuesUnavailableException noListsExist() {
        return new ListOfValuesUnavailableException(AttributeErrorCode.LIST_OF_VALUES_NOT_AVAILABLE);
    }

    /** The chosen list does not exist. */
    public static ListOfValuesUnavailableException notFound(String listOfValuesId) {
        return new ListOfValuesUnavailableException(AttributeErrorCode.LIST_OF_VALUES_NOT_FOUND, listOfValuesId);
    }

    public AttributeErrorCode code() {
        return code;
    }
}
