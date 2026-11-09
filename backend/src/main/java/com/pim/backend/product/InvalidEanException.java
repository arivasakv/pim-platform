package com.pim.backend.product;

import java.io.Serial;

/** Thrown when a value is not a valid EAN; carries the reason as an {@link EanErrorCode}. */
public class InvalidEanException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final EanErrorCode code;

    public InvalidEanException(EanErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public EanErrorCode code() {
        return code;
    }
}
