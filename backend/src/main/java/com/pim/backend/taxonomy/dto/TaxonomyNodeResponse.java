package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.NodeDetails;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import java.time.Instant;
import java.util.List;

/** One node with its path, children and effective attributes (contract {@code TaxonomyNode}). */
public record TaxonomyNodeResponse(
        String systemId,
        String name,
        String description,
        String parentId,
        int level,
        List<NodeReferenceResponse> path,
        List<NodeReferenceResponse> children,
        int directAttributeCount,
        List<EffectiveAttributeResponse> effectiveAttributes,
        Instant createdAt,
        Instant updatedAt) {

    public static TaxonomyNodeResponse from(NodeDetails details) {
        TaxonomyNode node = details.node();
        return new TaxonomyNodeResponse(
                node.systemId(),
                node.name().value(),
                node.description().orElse(null),
                node.parentId().orElse(null),
                details.level(),
                details.path().stream().map(NodeReferenceResponse::from).toList(),
                details.children().stream().map(NodeReferenceResponse::from).toList(),
                node.attributeIds().size(),
                details.attributes().stream()
                        .map(EffectiveAttributeResponse::from)
                        .toList(),
                node.createdAt(),
                node.updatedAt().orElse(null));
    }
}
