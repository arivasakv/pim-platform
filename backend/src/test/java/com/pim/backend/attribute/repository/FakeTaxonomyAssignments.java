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

    // --- Feature 014: list-value filters ---

    private final Map<String, List<TaxonomyNodeReference>> filtersByAttribute = new HashMap<>();
    private boolean failRemovingFilters;

    /** Gives the attribute list-value filters on these nodes (already sorted by name). */
    public synchronized void putFilters(String attributeId, TaxonomyNodeReference... nodes) {
        filtersByAttribute.put(attributeId.toUpperCase(Locale.ROOT), List.of(nodes));
    }

    /** Makes the next filter deletions fail after the action ran, as a storage failure would. */
    public synchronized void failRemovingFilters() {
        failRemovingFilters = true;
    }

    @Override
    public synchronized List<TaxonomyNodeReference> nodesFilteringValuesOf(String attributeId) {
        return filtersByAttribute.getOrDefault(attributeId.toUpperCase(Locale.ROOT), List.of());
    }

    @Override
    public synchronized void runRemovingValueFilters(String attributeId, Runnable action) {
        action.run();
        if (failRemovingFilters) {
            throw new IllegalStateException("Storage is down");
        }
        filtersByAttribute.remove(attributeId.toUpperCase(Locale.ROOT));
    }
}
