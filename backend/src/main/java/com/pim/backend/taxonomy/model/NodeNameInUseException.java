package com.pim.backend.taxonomy.model;

/** A sibling already has this name, ignoring letter case (spec FR-004; HTTP 409). Nothing changed. */
public class NodeNameInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NodeNameInUseException(NodeName name) {
        super(TaxonomyErrorCode.NODE_NAME_IN_USE.message(name.value()));
    }
}
