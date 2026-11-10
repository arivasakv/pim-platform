package com.pim.backend.attributegroup.model;

/**
 * Error codes of the attribute groups API with their Spanish messages (specs/005-attribute-groups/contracts,
 * {@code Problem.errors[].code}).
 */
public enum AttributeGroupErrorCode {
    REQUIRED("Este dato es obligatorio."),
    INVALID_GROUP_NAME("El nombre del grupo debe tener de 1 a 80 caracteres."),
    INVALID_GROUP_DESCRIPTION("La descripción del grupo puede tener hasta 500 caracteres."),
    INVALID_ATTRIBUTE_IDS("Indica de 1 a 100 IDs de atributo."),
    ATTRIBUTE_CREATE_NOT_PERMITTED("No tienes permiso para administrar grupos de atributos."),
    GROUP_NOT_FOUND("No existe un grupo con el ID %s."),
    ATTRIBUTE_NOT_IN_GROUP("El atributo %s no está en este grupo."),
    GROUP_NAME_IN_USE("Ya existe un grupo con el nombre \"%s\". Usa otro nombre."),
    GROUP_NOT_EMPTY("El grupo \"%s\" tiene %s atributos. Quítalos antes de eliminarlo."),
    ATTRIBUTE_NOT_FOUND("No existe un atributo con el ID %s.");

    private final String template;

    AttributeGroupErrorCode(String template) {
        this.template = template;
    }

    /** The Spanish message, with the arguments placed where the template names a value. */
    public String message(Object... arguments) {
        return String.format(template, arguments);
    }
}
