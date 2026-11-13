package com.pim.backend.taxonomy.model;

import java.util.Objects;

/**
 * A node in the flat list of the hierarchy (contract {@code TaxonomyNodeSummary}).
 *
 * @param node the node
 * @param level 1 for top-level nodes
 * @param childCount number of direct children
 */
public record NodeSummary(TaxonomyNode node, int level, int childCount) {

    public NodeSummary {
        Objects.requireNonNull(node, "node");
    }
}
