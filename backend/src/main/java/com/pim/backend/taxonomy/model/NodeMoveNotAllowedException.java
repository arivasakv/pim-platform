package com.pim.backend.taxonomy.model;

/**
 * A move that would break the hierarchy or change nothing (feature 011 FR-005, FR-006; HTTP 409). Nothing changed.
 */
public class NodeMoveNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final String ALREADY_TOP_LEVEL = "«%s» ya está en el primer nivel.";

    private final TaxonomyErrorCode code;

    private NodeMoveNotAllowedException(TaxonomyErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** The destination is the node itself or one of its descendants (FR-005). */
    public static NodeMoveNotAllowedException intoOwnBranch(TaxonomyNode node) {
        return new NodeMoveNotAllowedException(
                TaxonomyErrorCode.NODE_MOVE_INTO_OWN_BRANCH,
                TaxonomyErrorCode.NODE_MOVE_INTO_OWN_BRANCH.message(node.name().value()));
    }

    /** The destination is the node's current parent (FR-006). */
    public static NodeMoveNotAllowedException alreadyUnder(TaxonomyNode node, TaxonomyNode parent) {
        return new NodeMoveNotAllowedException(
                TaxonomyErrorCode.NODE_ALREADY_THERE,
                TaxonomyErrorCode.NODE_ALREADY_THERE.message(
                        node.name().value(), parent.name().value()));
    }

    /** A top-level node moved to the top level (FR-006). */
    public static NodeMoveNotAllowedException alreadyTopLevel(TaxonomyNode node) {
        return new NodeMoveNotAllowedException(
                TaxonomyErrorCode.NODE_ALREADY_THERE,
                String.format(ALREADY_TOP_LEVEL, node.name().value()));
    }

    /** {@code NODE_MOVE_INTO_OWN_BRANCH} or {@code NODE_ALREADY_THERE}. */
    public TaxonomyErrorCode code() {
        return code;
    }
}
