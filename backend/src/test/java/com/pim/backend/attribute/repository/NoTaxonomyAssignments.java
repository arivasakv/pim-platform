package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.TaxonomyNodeReference;
import java.util.List;

/**
 * Test fake: a {@link TaxonomyAssignmentDirectory} with no nodes, for unit tests that build {@code AttributeService}
 * by hand. Production uses the taxonomy feature's adapter (feature 010 plan decision D-7).
 */
public class NoTaxonomyAssignments implements TaxonomyAssignmentDirectory {

    @Override
    public List<TaxonomyNodeReference> runIfOnNoNode(String attributeId, Runnable action) {
        action.run();
        return List.of();
    }
}
