package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.NodeAttribute;

/** One attribute that applies to a node, with its assignment type and origin (contract {@code EffectiveAttribute}). */
public record EffectiveAttributeResponse(
        String systemId,
        String displayName,
        String dataType,
        String assignment,
        NodeReferenceResponse origin,
        NodeReferenceResponse alsoInheritedFrom) {

    public static EffectiveAttributeResponse from(NodeAttribute attribute) {
        return new EffectiveAttributeResponse(
                attribute.systemId(),
                attribute.displayName(),
                attribute.dataType(),
                attribute.effective().assignment().name(),
                NodeReferenceResponse.from(attribute.effective().origin()),
                attribute
                        .effective()
                        .alsoInheritedFrom()
                        .map(NodeReferenceResponse::from)
                        .orElse(null));
    }
}
