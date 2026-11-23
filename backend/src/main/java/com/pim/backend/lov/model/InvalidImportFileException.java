package com.pim.backend.lov.model;

/**
 * A CSV or Excel file cannot be imported as a whole (HTTP 422): {@code INVALID_IMPORT_FILE} (unreadable, missing or unknown
 * columns) or {@code IMPORT_TOO_LARGE} (over 5 MB or 50,000 rows). Nothing was applied.
 */
public class InvalidImportFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final LovErrorCode code;

    private InvalidImportFileException(LovErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** The file cannot be read; {@code reason} says why, in Spanish, without a final period. */
    public static InvalidImportFileException unreadable(String reason) {
        return new InvalidImportFileException(
                LovErrorCode.INVALID_IMPORT_FILE, LovErrorCode.INVALID_IMPORT_FILE.message(reason));
    }

    /** The file has more than 50,000 rows or more than 5 MB. */
    public static InvalidImportFileException tooLarge() {
        return new InvalidImportFileException(LovErrorCode.IMPORT_TOO_LARGE, LovErrorCode.IMPORT_TOO_LARGE.message());
    }

    public LovErrorCode code() {
        return code;
    }
}
