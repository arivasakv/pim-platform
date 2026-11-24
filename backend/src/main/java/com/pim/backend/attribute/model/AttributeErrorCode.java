package com.pim.backend.attribute.model;

/**
 * Error codes of the attribute API with their Spanish messages (specs/004-attribute-creation/contracts). Feature
 * 003's errors keep feature 003's own codes and messages (FR-005).
 */
public enum AttributeErrorCode {
    REQUIRED("Este dato es obligatorio."),
    INVALID_DISPLAY_NAME("El nombre debe tener de 1 a 120 caracteres."),
    UNKNOWN_DATA_TYPE("El tipo de dato %s no existe."),
    INVALID_LENGTH("La longitud debe ser un número entero entre %s y 100000."),
    INVALID_LENGTH_RANGE("La longitud mínima no puede ser mayor que la longitud máxima."),
    UNKNOWN_CASE_FORMAT("El formato de mayúsculas %s no existe."),
    INVALID_CAPTURE_MASK("La máscara de captura debe ser una expresión regular válida de hasta 500 caracteres."),
    UNKNOWN_NUMBER_FORMAT("El formato numérico %s no existe."),
    INVALID_PRECISION("Los decimales van de 1 a 10 y solo aplican a números decimales."),
    INVALID_NUMBER("El valor no es válido para este formato numérico."),
    INVALID_VALUE_RANGE("El valor mínimo no puede ser mayor que el valor máximo."),
    UNKNOWN_SELECTION_MODE("El tipo de selección %s no existe."),
    UNKNOWN_DATE_FORMAT("El formato de fecha %s no existe."),
    UNKNOWN_CURRENCY("La moneda %s no es un código ISO 4217 válido."),
    INVALID_DECIMALS("Los decimales de una moneda van de 0 a 4."),
    UNKNOWN_TARGET_OBJECT_TYPE("El tipo de objeto %s no existe."),
    UNKNOWN_CARDINALITY("La cantidad %s no existe."),
    ATTRIBUTE_CREATE_NOT_PERMITTED("No tienes permiso para crear atributos."),
    DISPLAY_NAME_IN_USE("Ya existe un atributo con el nombre \"%s\". Usa otro nombre."),
    LIST_OF_VALUES_NOT_AVAILABLE(
            "Aún no existen listas de valores. Un atributo de tipo lista de valores necesita una lista existente."),
    LIST_OF_VALUES_NOT_FOUND("No existe la lista de valores %s."),
    ATTRIBUTE_CHANGE_NOT_PERMITTED("No tienes permiso para modificar atributos."),
    SYSTEM_ID_IMMUTABLE("El ID de sistema no se puede cambiar."),
    DATA_TYPE_IMMUTABLE("El tipo de dato no se puede cambiar."),
    ATTRIBUTE_IN_GROUPS("El atributo pertenece a %s: %s. Quítalo de esos grupos antes de eliminarlo."),
    ATTRIBUTE_ON_TAXONOMY_NODES(
            "El atributo pertenece a %s de la taxonomía: %s. Quítalo de esos nodos antes de eliminarlo."),
    // Feature 014: unlinking a list of values from an attribute.
    ATTRIBUTE_NOT_LIST_OF_VALUES(
            "El atributo «%s» no es de tipo lista de valores; no tiene una lista que desvincular."),
    UNLINK_TO_LIST_NOT_ALLOWED("Elige un tipo de dato distinto de lista de valores para el atributo."),
    UNLINK_OUTDATED("El atributo o sus filtros cambiaron desde la revisión. Vuelve a revisar la desvinculación."),
    REVIEW_TOKEN_REQUIRED("Falta el resultado de la revisión. Revisa la desvinculación antes de confirmarla.");

    private final String template;

    AttributeErrorCode(String template) {
        this.template = template;
    }

    /** The Spanish message, with the arguments placed where the template names a value. */
    public String message(Object... arguments) {
        return String.format(template, arguments);
    }
}
