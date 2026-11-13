package com.pim.backend.taxonomy.model;

/** The attribute is only inherited on this node, so it is removed in its origin node (spec FR-010; HTTP 409). */
public class AttributeNotDirectException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttributeNotDirectException(String attributeName, NodeReference origin) {
        super(TaxonomyErrorCode.ATTRIBUTE_NOT_DIRECT.message(attributeName, origin.name()));
    }
}
