package com.pim.backend.taxonomy.model;

/**
 * Error codes of the taxonomy API with their Spanish messages (specs/010-taxonomy-attribute-inheritance/contracts,
 * {@code Problem.errors[].code}).
 */
public enum TaxonomyErrorCode {
    REQUIRED("Este dato es obligatorio."),
    INVALID_NODE_NAME("El nombre del nodo debe tener de 1 a 80 caracteres."),
    INVALID_NODE_DESCRIPTION("La descripción del nodo puede tener hasta 500 caracteres."),
    INVALID_ATTRIBUTE_IDS("Indica de 1 a 100 IDs de atributo."),
    TAXONOMY_CHANGE_NOT_PERMITTED("No tienes permiso para administrar la taxonomía."),
    NODE_NOT_FOUND("No existe un nodo con el ID %s."),
    NODE_NAME_IN_USE("Ya existe un nodo «%s» en este nivel. Usa otro nombre."),
    NODE_MOVE_INTO_OWN_BRANCH("No puedes mover «%s» dentro de sí mismo ni de uno de sus nodos descendientes."),
    NODE_ALREADY_THERE("«%s» ya está en «%s»."),
    NODE_HAS_CHILDREN("El nodo «%s» tiene %s. Elimínalos antes de eliminar este nodo."),
    NODE_HAS_ATTRIBUTES("El nodo «%s» tiene %s. Quítalos antes de eliminar el nodo."),
    ATTRIBUTE_NOT_FOUND("No existe un atributo con el ID %s."),
    ATTRIBUTE_NOT_DIRECT("El atributo «%s» se hereda de «%s». Quítalo en ese nodo."),
    ATTRIBUTE_NOT_ON_NODE("El atributo %s no está asignado a este nodo ni lo hereda.");

    private final String template;

    TaxonomyErrorCode(String template) {
        this.template = template;
    }

    /** The Spanish message, with the arguments placed where the template names a value. */
    public String message(Object... arguments) {
        return String.format(template, arguments);
    }
}
