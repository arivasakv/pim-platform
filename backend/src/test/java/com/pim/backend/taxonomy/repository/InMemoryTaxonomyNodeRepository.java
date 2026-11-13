package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.NodeName;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Test fake of {@link TaxonomyNodeRepository}, kept in memory for unit tests. Production code stores nodes in
 * MongoDB ({@link MongoTaxonomyNodeRepository}, ADR 0003). Not thread-safe by itself: {@code TaxonomyService} calls
 * it under one lock.
 */
public class InMemoryTaxonomyNodeRepository implements TaxonomyNodeRepository {

    private final Map<String, TaxonomyNode> nodes = new LinkedHashMap<>();

    @Override
    public Optional<TaxonomyNode> findById(String systemId) {
        return Optional.ofNullable(nodes.get(TaxonomyNode.key(systemId)));
    }

    @Override
    public List<TaxonomyNode> findAll() {
        return nodes.values().stream()
                .sorted(Comparator.comparing(node -> node.name().comparisonKey()))
                .toList();
    }

    @Override
    public List<TaxonomyNode> findAllListing(String attributeId) {
        return findAll().stream().filter(node -> node.hasDirect(attributeId)).toList();
    }

    @Override
    public boolean existsWithName(String parentId, NodeName name, String excludedId) {
        String parent = parentId == null ? null : TaxonomyNode.key(parentId);
        String excluded = excludedId == null ? null : TaxonomyNode.key(excludedId);
        return nodes.values().stream()
                .anyMatch(node -> Objects.equals(node.parentId().orElse(null), parent)
                        && node.name().equals(name)
                        && !node.id().equals(excluded));
    }

    @Override
    public void insert(TaxonomyNode node) {
        if (nodes.containsKey(node.id()) || existsWithName(node.parentId().orElse(null), node.name(), null)) {
            throw new IllegalStateException("Node already stored: " + node.name());
        }
        nodes.put(node.id(), node);
    }

    @Override
    public void update(TaxonomyNode node) {
        if (existsWithName(node.parentId().orElse(null), node.name(), node.id())) {
            throw new IllegalStateException("Node name already stored among siblings: " + node.name());
        }
        nodes.put(node.id(), node);
    }

    @Override
    public void delete(String systemId) {
        nodes.remove(TaxonomyNode.key(systemId));
    }
}
