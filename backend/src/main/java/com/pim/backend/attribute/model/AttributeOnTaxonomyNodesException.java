package com.pim.backend.attribute.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The attribute is assigned to taxonomy nodes, so it cannot be deleted until it is removed from them (feature 010
 * FR-021; HTTP 409). Nothing changed.
 */
public class AttributeOnTaxonomyNodesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<TaxonomyNodeReference> nodes;

    public AttributeOnTaxonomyNodesException(List<TaxonomyNodeReference> nodes) {
        super(message(nodes));
        this.nodes = List.copyOf(nodes);
    }

    /** The nodes that list the attribute. */
    public List<TaxonomyNodeReference> nodes() {
        return nodes;
    }

    private static String message(List<TaxonomyNodeReference> nodes) {
        String count = nodes.size() == 1 ? "1 nodo" : nodes.size() + " nodos";
        String names = nodes.stream().map(TaxonomyNodeReference::name).collect(Collectors.joining(", "));
        return AttributeErrorCode.ATTRIBUTE_ON_TAXONOMY_NODES.message(count, names);
    }
}
