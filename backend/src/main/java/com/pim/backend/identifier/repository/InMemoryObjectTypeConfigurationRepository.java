package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link ObjectTypeConfigurationRepository} (constitution IX; plan decision D-1). Not thread-safe by
 * itself: {@code IdentifierService} calls it under one lock (research R-10).
 */
@Repository
public class InMemoryObjectTypeConfigurationRepository implements ObjectTypeConfigurationRepository {

    private final Map<ObjectType, ObjectTypeConfiguration> configurations = new EnumMap<>(ObjectType.class);

    @Override
    public List<ObjectTypeConfiguration> findAll() {
        return List.copyOf(configurations.values());
    }

    @Override
    public ObjectTypeConfiguration find(ObjectType objectType) {
        ObjectTypeConfiguration configuration = configurations.get(objectType);
        if (configuration == null) {
            throw new IllegalStateException("Object type not configured: " + objectType);
        }
        return configuration;
    }

    @Override
    public void save(ObjectTypeConfiguration configuration) {
        configurations.put(configuration.objectType(), configuration);
    }
}
