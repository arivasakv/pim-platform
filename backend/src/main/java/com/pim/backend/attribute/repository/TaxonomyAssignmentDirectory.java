package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.TaxonomyNodeReference;
import java.util.List;

/**
 * The taxonomy nodes that list attributes directly (feature 010 plan decision D-7, research R-05). Implemented by the
 * taxonomy feature ({@code TaxonomyAssignmentDirectoryAdapter}) on top of its own service and lock, so this domain
 * never reads nodes directly (constitution III).
 *
 * <p>Lock order is group → taxonomy → attribute: callers must not hold the attribute lock while calling this method.
 */
public interface TaxonomyAssignmentDirectory {

    /**
     * Runs {@code action} only if no node lists the attribute directly (compared ignoring letter case), while holding
     * the taxonomy lock, so no node can add the attribute in between.
     *
     * @return the nodes that list the attribute, sorted by name ignoring letter case; empty if the action ran
     */
    List<TaxonomyNodeReference> runIfOnNoNode(String attributeId, Runnable action);
}
