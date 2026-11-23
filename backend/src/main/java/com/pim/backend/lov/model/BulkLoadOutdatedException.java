package com.pim.backend.lov.model;

/**
 * The bulk load file, or the lists it touches, changed since it was checked (feature 013 FR-010, research R-05; HTTP
 * 409 {@code BULK_LOAD_OUTDATED}). Nothing was saved.
 */
public class BulkLoadOutdatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BulkLoadOutdatedException() {
        super(LovErrorCode.BULK_LOAD_OUTDATED.message());
    }
}
