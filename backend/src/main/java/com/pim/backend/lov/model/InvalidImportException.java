package com.pim.backend.lov.model;

import java.util.List;

/** A CSV import has wrong rows (FR-016; HTTP 422 {@code INVALID_IMPORT}). Nothing was applied. */
public class InvalidImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ImportRowError> rowErrors;

    public InvalidImportException(List<ImportRowError> rowErrors) {
        super(LovErrorCode.INVALID_IMPORT.message(rowErrors.size() == 1 ? "1 error" : rowErrors.size() + " errores"));
        this.rowErrors = List.copyOf(rowErrors);
    }

    /** Every wrong row, in file order. */
    public List<ImportRowError> rowErrors() {
        return rowErrors;
    }
}
