package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.ValueFilter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stores list-value filters (feature 012 data-model). Holds no business rules (constitution XII); {@code
 * TaxonomyService} checks nodes, attributes and values before saving. IDs are compared ignoring letter case.
 */
public interface ValueFilterRepository {

    /** The node's filter for the attribute. */
    Optional<ValueFilter> find(String nodeId, String attributeId);

    /** Every filter on these nodes, for every attribute. */
    List<ValueFilter> findByNodeIds(Collection<String> nodeIds);

    /** Saves a new filter or replaces the node's filter for the same attribute. */
    void save(ValueFilter filter);

    void delete(String nodeId, String attributeId);

    /** Removes every filter of the node (feature 012 plan D-6). */
    void deleteByNode(String nodeId);
}
