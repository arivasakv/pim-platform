package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.TaxonomyNodeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Test fake: a {@link TaxonomyAssignmentDirectory} whose assignments a test sets with {@link #put}. */
public class FakeTaxonomyAssignments implements TaxonomyAssignmentDirectory {

    private final Map<String, List<TaxonomyNodeReference>> byAttribute = new HashMap<>();

    /** Lists the attribute on these nodes (in this order); none removes it. */
    public synchronized void put(String attributeId, TaxonomyNodeReference... nodes) {
        byAttribute.put(attributeId.toUpperCase(Locale.ROOT), List.of(nodes));
    }

    @Override
    public synchronized List<TaxonomyNodeReference> runIfOnNoNode(String attributeId, Runnable action) {
        List<TaxonomyNodeReference> nodes = byAttribute.getOrDefault(attributeId.toUpperCase(Locale.ROOT), List.of());
        if (nodes.isEmpty()) {
            action.run();
        }
        return nodes;
    }
}
