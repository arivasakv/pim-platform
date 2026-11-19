package com.pim.backend.taxonomy.model;

/**
 * A sibling already has this name, ignoring letter case (spec FR-004; feature 011 FR-007; HTTP 409). Nothing
 * changed.
 */
public class NodeNameInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final String AT_DESTINATION =
            "Ya existe un nodo «%s» en «%s». Cambia el nombre de uno de los dos antes de mover.";
    private static final String AT_TOP_LEVEL =
            "Ya existe un nodo «%s» en el primer nivel. Cambia el nombre de uno de los dos antes de mover.";

    private final String field;

    /** Creating or renaming: the name typed is in use (field {@code name}). */
    public NodeNameInUseException(NodeName name) {
        this(TaxonomyErrorCode.NODE_NAME_IN_USE.message(name.value()), "name");
    }

    private NodeNameInUseException(String message, String field) {
        super(message);
        this.field = field;
    }

    /**
     * Moving: the destination already has a child with the node's name (feature 011 FR-007; field {@code parentId}).
     *
     * @param destination the new parent, or {@code null} for the top level
     */
    public static NodeNameInUseException atDestination(NodeName name, TaxonomyNode destination) {
        String message = destination == null
                ? String.format(AT_TOP_LEVEL, name.value())
                : String.format(AT_DESTINATION, name.value(), destination.name().value());
        return new NodeNameInUseException(message, "parentId");
    }

    /** The request field the conflict refers to: {@code name} or {@code parentId}. */
    public String field() {
        return field;
    }
}
