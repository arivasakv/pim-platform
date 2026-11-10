package com.pim.backend.attributegroup.repository;

import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.GroupName;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Test fake of {@link AttributeGroupRepository}, kept in memory for unit tests. Production code stores groups in
 * MongoDB ({@link MongoAttributeGroupRepository}, ADR 0003). Not thread-safe by itself: {@code AttributeGroupService}
 * calls it under one lock.
 */
public class InMemoryAttributeGroupRepository implements AttributeGroupRepository {

    private final Map<String, AttributeGroup> groups = new LinkedHashMap<>();

    @Override
    public Optional<AttributeGroup> findById(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    @Override
    public List<AttributeGroup> findAll() {
        return groups.values().stream()
                .sorted(Comparator.comparing(group -> group.name().comparisonKey()))
                .toList();
    }

    @Override
    public boolean existsWithName(GroupName name, String excludedId) {
        return groups.values().stream()
                .anyMatch(group -> group.name().equals(name) && !Objects.equals(group.id(), excludedId));
    }

    @Override
    public void insert(AttributeGroup group) {
        if (groups.containsKey(group.id()) || existsWithName(group.name(), null)) {
            throw new IllegalStateException("Group already stored: " + group.name());
        }
        groups.put(group.id(), group);
    }

    @Override
    public void update(AttributeGroup group) {
        if (existsWithName(group.name(), group.id())) {
            throw new IllegalStateException("Group name already stored: " + group.name());
        }
        groups.put(group.id(), group);
    }

    @Override
    public void delete(String id) {
        groups.remove(id);
    }
}
