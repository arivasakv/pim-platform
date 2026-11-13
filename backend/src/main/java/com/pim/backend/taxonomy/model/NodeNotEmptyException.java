package com.pim.backend.taxonomy.model;

/**
 * A node with children or direct attributes cannot be deleted (spec FR-006; HTTP 409). Nothing changed.
 */
public class NodeNotEmptyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TaxonomyErrorCode code;

    private NodeNotEmptyException(TaxonomyErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** The node still has children. */
    public static NodeNotEmptyException withChildren(TaxonomyNode node, int count) {
        String children = count == 1 ? "1 nodo hijo" : count + " nodos hijos";
        return new NodeNotEmptyException(
                TaxonomyErrorCode.NODE_HAS_CHILDREN,
                TaxonomyErrorCode.NODE_HAS_CHILDREN.message(node.name().value(), children));
    }

    /** The node still has direct attributes. */
    public static NodeNotEmptyException withAttributes(TaxonomyNode node) {
        int count = node.attributeIds().size();
        String attributes = count == 1 ? "1 atributo asignado" : count + " atributos asignados";
        return new NodeNotEmptyException(
                TaxonomyErrorCode.NODE_HAS_ATTRIBUTES,
                TaxonomyErrorCode.NODE_HAS_ATTRIBUTES.message(node.name().value(), attributes));
    }

    /** {@code NODE_HAS_CHILDREN} or {@code NODE_HAS_ATTRIBUTES}. */
    public TaxonomyErrorCode code() {
        return code;
    }
}
