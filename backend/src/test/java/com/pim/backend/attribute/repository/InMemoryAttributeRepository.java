package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.DisplayName;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Test fake of {@link AttributeRepository}, kept in memory for unit tests. Production code stores attributes in
 * MongoDB ({@link MongoAttributeRepository}, ADR 0003). Not thread-safe by itself: {@code AttributeService} calls it
 * under one lock (research R-18).
 */
public class InMemoryAttributeRepository implements AttributeRepository {

    private final Map<String, Attribute> bySystemId = new HashMap<>();
    private final Map<DisplayName, Attribute> byDisplayName = new HashMap<>();

    @Override
    public Optional<Attribute> findBySystemId(String systemId) {
        return Optional.ofNullable(bySystemId.get(key(systemId)));
    }

    @Override
    public boolean existsWithDisplayName(DisplayName displayName) {
        return byDisplayName.containsKey(displayName);
    }

    @Override
    public boolean existsWithDisplayName(DisplayName displayName, String exceptSystemId) {
        Attribute holder = byDisplayName.get(displayName);
        return holder != null && !key(holder.systemId()).equals(key(exceptSystemId));
    }

    @Override
    public void update(Attribute attribute) {
        String idKey = key(attribute.systemId());
        Attribute holder = byDisplayName.get(attribute.displayName());
        if (holder != null && !key(holder.systemId()).equals(idKey)) {
            throw new IllegalStateException("Display name already stored: " + attribute.displayName());
        }
        Attribute previous = bySystemId.put(idKey, attribute);
        if (previous != null) {
            byDisplayName.remove(previous.displayName());
        }
        byDisplayName.put(attribute.displayName(), attribute);
    }

    @Override
    public void delete(String systemId) {
        Attribute removed = bySystemId.remove(key(systemId));
        if (removed != null) {
            byDisplayName.remove(removed.displayName());
        }
    }

    @Override
    public void save(Attribute attribute) {
        String idKey = key(attribute.systemId());
        if (bySystemId.containsKey(idKey) || byDisplayName.containsKey(attribute.displayName())) {
            throw new IllegalStateException("Attribute already stored: " + attribute.systemId());
        }
        bySystemId.put(idKey, attribute);
        byDisplayName.put(attribute.displayName(), attribute);
    }

    @Override
    public List<Attribute> findAll() {
        return bySystemId.values().stream()
                .sorted(Comparator.comparing(
                        attribute -> attribute.displayName().comparisonKey()))
                .toList();
    }

    @Override
    public int count() {
        return bySystemId.size();
    }

    private static String key(String systemId) {
        return systemId.toUpperCase(Locale.ROOT);
    }
}
