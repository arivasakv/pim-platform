package com.pim.backend.taxonomy.model;

import java.util.List;
import java.util.Objects;

/**
 * A node with its place in the hierarchy and its effective attributes (spec FR-007, FR-013; contract {@code
 * TaxonomyNode}).
 *
 * @param node the node
 * @param level 1 for top-level nodes
 * @param path ancestors from the top level down to the parent
 * @param children direct children sorted by name ignoring letter case
 * @param attributes effective attributes in the order of FR-016
 * @param listAttributes the effective list-of-values attributes with their allowed values (feature 012 FR-013)
 */
public record NodeDetails(
        TaxonomyNode node,
        int level,
        List<NodeReference> path,
        List<NodeReference> children,
        List<NodeAttribute> attributes,
        List<ListAttributeSummary> listAttributes) {

    public NodeDetails {
        Objects.requireNonNull(node, "node");
        path = List.copyOf(path);
        children = List.copyOf(children);
        attributes = List.copyOf(attributes);
        listAttributes = List.copyOf(listAttributes);
    }
}
