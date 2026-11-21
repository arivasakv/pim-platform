package com.pim.backend.identifier.model;

/**
 * Stable, machine-readable error codes of the identifier API, each with its Spanish message (FR-011). The codes
 * are those of specs/003-identifier-management/contracts/identifiers.openapi.yaml.
 */
public enum IdentifierErrorCode {
    REQUIRED("Este dato es obligatorio."),
    UNKNOWN_OBJECT_TYPE("El tipo de objeto %s no existe."),
    INVALID_SYSTEM_ID("El ID solo puede tener de 1 a 40 letras, números, guiones, guiones bajos o puntos."),
    MISSING_KEY_FIELD("Falta el valor del campo llave %s."),
    UNKNOWN_KEY_FIELD("%s no es un campo llave de este tipo de objeto."),
    INVALID_KEY_FIELD_VALUE("El valor del campo llave %s debe tener de 1 a 200 caracteres."),
    INVALID_KEY_FIELD_NAME("Los campos llave deben ser hasta 20 nombres distintos de 1 a 60 letras, números o"
            + " guiones bajos, que empiecen con una letra."),
    INVALID_SEQUENCE_KIND("El tipo de secuencia debe ser NUMERIC o ALPHANUMERIC."),
    INVALID_PREFIX("Una secuencia ALPHANUMERIC necesita un prefijo de 1 a 10 letras mayúsculas; una secuencia"
            + " NUMERIC no lleva prefijo."),
    INVALID_MINIMUM_DIGITS("El número mínimo de dígitos debe estar entre 1 y 18."),
    INVALID_NEXT_VALUE("El siguiente valor debe estar entre 1 y 999999999999999999."),
    MANUAL_ID_NOT_PERMITTED("No tienes permiso para definir el ID %s para un objeto de tipo %s."),
    SYSTEM_ID_IN_USE("El ID %s ya se encuentra en uso por otro objeto. Favor de asignar un identificador inédito."),
    SEQUENCE_EXHAUSTED("La secuencia de IDs de %s llegó a su valor máximo.");

    private final String template;

    IdentifierErrorCode(String template) {
        this.template = template;
    }

    /** The Spanish message, with the arguments placed where the template names a value. */
    public String message(Object... arguments) {
        return String.format(template, arguments);
    }
}
