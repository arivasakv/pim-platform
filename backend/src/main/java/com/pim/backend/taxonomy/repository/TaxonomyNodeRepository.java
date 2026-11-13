package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.NodeName;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import java.util.List;
import java.util.Optional;

/**
 * Stores taxonomy nodes and their direct attributes. Holds no business rules (constitution XII); {@code
 * TaxonomyService} checks names, parents and attributes before saving.
 */
public interface TaxonomyNodeRepository {

    /** The node with this system ID, compared ignoring letter case. */
    Optional<TaxonomyNode> findById(String systemId);

    /** Every node, sorted by name ignoring letter case. */
    List<TaxonomyNode> findAll();

    /** The nodes that list this attribute directly, compared ignoring letter case, sorted by name. */
    List<TaxonomyNode> findAllListing(String attributeId);

    /**
     * Whether a node under the same parent (or at the top level), other than {@code excludedId}, has this name
     * ignoring letter case.
     *
     * @param parentId the parent's system ID, or {@code null} for the top level
     * @param excludedId the node being renamed, or {@code null} when creating
     */
    boolean existsWithName(String parentId, NodeName name, String excludedId);

    /**
     * Saves a new node.
     *
     * @throws IllegalStateException if its system ID or its name among siblings is already stored
     */
    void insert(TaxonomyNode node);

    /**
     * Replaces a stored node (same system ID).
     *
     * @throws IllegalStateException if a sibling has its name
     */
    void update(TaxonomyNode node);

    void delete(String systemId);
}
