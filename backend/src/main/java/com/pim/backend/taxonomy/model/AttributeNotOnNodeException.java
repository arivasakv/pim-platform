package com.pim.backend.taxonomy.model;

/** The attribute is neither assigned to nor inherited by the node (HTTP 404). Nothing changed. */
public class AttributeNotOnNodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeNotOnNodeException(String attributeId) {
        super(TaxonomyErrorCode.ATTRIBUTE_NOT_ON_NODE.message(attributeId));
    }
}
