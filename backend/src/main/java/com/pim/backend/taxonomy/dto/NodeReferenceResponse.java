package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.NodeReference;

/** A node named in a path, a list of children or an origin (contract {@code NodeReference}). */
public record NodeReferenceResponse(String systemId, String name) {

    public static NodeReferenceResponse from(NodeReference reference) {
        return new NodeReferenceResponse(reference.systemId(), reference.name());
    }
}
