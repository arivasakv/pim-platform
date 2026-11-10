package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.DisplayName;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link AttributeRepository} (constitution IX; plan decision D-1): attributes are lost when the backend
 * stops. Not thread-safe by itself: {@code AttributeService} calls it under one lock (research R-18).
 */
@Repository
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
    public void save(Attribute attribute) {
        String idKey = key(attribute.systemId());
        if (bySystemId.containsKey(idKey) || byDisplayName.containsKey(attribute.displayName())) {
            throw new IllegalStateException("Attribute already stored: " + attribute.systemId());
        }
        bySystemId.put(idKey, attribute);
        byDisplayName.put(attribute.displayName(), attribute);
    }

    @Override
    public int count() {
        return bySystemId.size();
    }

    private static String key(String systemId) {
        return systemId.toUpperCase(Locale.ROOT);
    }
}
