package com.pim.backend.taxonomy.model;

/** No node has the requested system ID (HTTP 404). Carries the request field that named it. */
public class NodeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public NodeNotFoundException(String systemId, String field) {
        super(TaxonomyErrorCode.NODE_NOT_FOUND.message(systemId));
        this.field = field;
    }

    /** {@code nodeId} for the path, {@code parentId} for the parent of a new node. */
    public String field() {
        return field;
    }
}
