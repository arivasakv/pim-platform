package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.NodeSummary;

/** A node in the flat list of the hierarchy (contract {@code TaxonomyNodeSummary}). */
public record TaxonomyNodeSummaryResponse(
        String systemId, String name, String parentId, int level, int directAttributeCount, int childCount) {

    public static TaxonomyNodeSummaryResponse from(NodeSummary summary) {
        return new TaxonomyNodeSummaryResponse(
                summary.node().systemId(),
                summary.node().name().value(),
                summary.node().parentId().orElse(null),
                summary.level(),
                summary.node().attributeIds().size(),
                summary.childCount());
    }
}
