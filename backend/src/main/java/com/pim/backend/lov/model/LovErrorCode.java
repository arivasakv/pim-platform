package com.pim.backend.lov.model;

/**
 * Error codes of the lists-of-values API with their Spanish messages (specs/006-lov-management/contracts,
 * specs/013-lov-bulk-load/contracts; {@code Problem.errors[].code} and {@code rowErrors[].code}).
 */
public enum LovErrorCode {
    REQUIRED("Este dato es obligatorio."),
    INVALID_LIST_NAME("El nombre de la lista debe tener de 1 a 80 caracteres."),
    INVALID_VALUE_ID("El ID debe tener de 1 a 40 caracteres: letras, números, guion, guion bajo o punto."),
    INVALID_LABEL("La etiqueta debe tener de 1 a 200 caracteres."),
    INVALID_EQUIVALENCE("La equivalencia puede tener hasta 200 caracteres."),
    ATTRIBUTE_CREATE_NOT_PERMITTED("No tienes permiso para administrar listas de valores."),
    LIST_NOT_FOUND("No existe una lista de valores con el ID %s."),
    VALUE_NOT_FOUND("No existe un valor con el ID %s en esta lista."),
    LIST_NAME_IN_USE("Ya existe una lista con el nombre \"%s\". Usa otro nombre."),
    VALUE_ID_IN_USE("Ya existe un valor con el ID \"%s\" en esta lista."),
    LIST_IN_USE("La lista \"%s\" está vinculada a %s y no se puede eliminar."),
    LIST_TOO_LARGE("Una lista puede tener como máximo 50,000 valores. No se aplicó ningún cambio."),
    INVALID_IMPORT("El archivo tiene %s. No se aplicó ningún valor."),
    INVALID_IMPORT_FILE("No se pudo leer el archivo: %s. No se aplicó ningún valor."),
    IMPORT_TOO_LARGE("El archivo supera el límite de 5 MB o de 50,000 filas. No se aplicó ningún valor."),
    DUPLICATE_ID_IN_FILE("El ID \"%s\" está repetido en el archivo (filas %s)."),
    // Feature 013 (bulk load of lists of values). The next two are message templates for the codes
    // DUPLICATE_ID_IN_FILE and LIST_TOO_LARGE, which the API keeps (contract lov-bulk-load.openapi.yaml).
    DUPLICATE_ID_IN_LIST_OF_FILE("El ID \"%s\" de la lista «%s» está repetido en el archivo (filas %s)."),
    LIST_TOO_LARGE_IN_FILE(
            "La lista «%s» quedaría con %s valores; una lista puede tener como máximo 50,000. No se aplicó ningún"
                    + " valor."),
    BULK_LOAD_OUTDATED("Las listas cambiaron desde la revisión. Vuelve a revisar el archivo."),
    CHECK_TOKEN_REQUIRED("Falta el resultado de la revisión del archivo. Revisa el archivo antes de cargarlo."),
    INVALID_FORMAT("El formato debe ser csv o xlsx.");

    private final String template;

    LovErrorCode(String template) {
        this.template = template;
    }

    /** The Spanish message, with the arguments placed where the template names a value. */
    public String message(Object... arguments) {
        return String.format(template, arguments);
    }
}
