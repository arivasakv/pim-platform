package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.TaxonomyNode;
import com.pim.backend.taxonomy.model.ValueFilter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Test double of {@link ValueFilterRepository}, keyed like the MongoDB documents. */
public class InMemoryValueFilterRepository implements ValueFilterRepository {

    private final Map<String, ValueFilter> filters = new ConcurrentHashMap<>();

    @Override
    public Optional<ValueFilter> find(String nodeId, String attributeId) {
        return Optional.ofNullable(filters.get(key(nodeId, attributeId)));
    }

    @Override
    public List<ValueFilter> findByNodeIds(Collection<String> nodeIds) {
        Set<String> keys = nodeIds.stream().map(TaxonomyNode::key).collect(Collectors.toSet());
        return filters.values().stream()
                .filter(filter -> keys.contains(filter.nodeId()))
                .toList();
    }

    @Override
    public void save(ValueFilter filter) {
        filters.put(filter.id(), filter);
    }

    @Override
    public void delete(String nodeId, String attributeId) {
        filters.remove(key(nodeId, attributeId));
    }

    @Override
    public void deleteByNode(String nodeId) {
        String key = TaxonomyNode.key(nodeId);
        filters.values().removeIf(filter -> filter.nodeId().equals(key));
    }

    @Override
    public List<ValueFilter> findByAttribute(String attributeId) {
        String key = TaxonomyNode.key(attributeId);
        return filters.values().stream()
                .filter(filter -> filter.attributeId().equals(key))
                .toList();
    }

    @Override
    public void deleteByAttribute(String attributeId) {
        String key = TaxonomyNode.key(attributeId);
        filters.values().removeIf(filter -> filter.attributeId().equals(key));
    }

    /** Every stored filter. */
    public List<ValueFilter> all() {
        return List.copyOf(filters.values());
    }

    private static String key(String nodeId, String attributeId) {
        return TaxonomyNode.key(nodeId) + ":" + TaxonomyNode.key(attributeId);
    }
}
