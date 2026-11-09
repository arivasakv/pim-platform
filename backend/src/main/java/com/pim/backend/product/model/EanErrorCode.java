package com.pim.backend.product.model;

/**
 * Why an EAN was rejected. The code is the stable value clients rely on; the message is the Spanish
 * text shown to the user (docs/decisions/0001-api-conventions.md).
 */
public enum EanErrorCode {
    REQUIRED("Escribe un EAN."),
    TOO_LONG("El EAN no puede tener más de 20 caracteres."),
    INVALID_CHARACTERS("El EAN solo puede contener números."),
    INVALID_LENGTH("El EAN debe tener 8, 12, 13 o 14 dígitos.");

    private final String message;

    EanErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
