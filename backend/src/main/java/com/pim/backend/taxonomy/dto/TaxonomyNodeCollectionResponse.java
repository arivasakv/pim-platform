package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.NodeSummary;
import java.util.List;

/**
 * Every node, in the project-wide collection envelope (docs/decisions/0001-api-conventions.md; contract {@code
 * TaxonomyNodeCollection}).
 */
public record TaxonomyNodeCollectionResponse(List<TaxonomyNodeSummaryResponse> items, int total) {

    public static TaxonomyNodeCollectionResponse of(List<NodeSummary> nodes) {
        List<TaxonomyNodeSummaryResponse> items =
                nodes.stream().map(TaxonomyNodeSummaryResponse::from).toList();
        return new TaxonomyNodeCollectionResponse(items, items.size());
    }
}
