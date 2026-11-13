package com.pim.backend.taxonomy.model;

/** The user lacks {@code ATTRIBUTE_CREATE}, needed to change the taxonomy (spec FR-017; HTTP 403). Nothing changed. */
public class TaxonomyChangeNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaxonomyChangeNotPermittedException() {
        super(TaxonomyErrorCode.TAXONOMY_CHANGE_NOT_PERMITTED.message());
    }
}
