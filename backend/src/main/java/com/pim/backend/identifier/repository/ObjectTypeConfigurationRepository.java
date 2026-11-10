package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import java.util.List;

/** Stores the configuration of each object type (FR-006, FR-013). Holds no business rules (constitution XII). */
public interface ObjectTypeConfigurationRepository {

    /** All configurations, in {@link ObjectType} order. */
    List<ObjectTypeConfiguration> findAll();

    /**
     * The configuration of one type.
     *
     * @throws IllegalStateException if the type has not been configured (the defaults are seeded at startup)
     */
    ObjectTypeConfiguration find(ObjectType objectType);

    /** Whether the type has a stored configuration (defaults are only seeded for types without one). */
    boolean contains(ObjectType objectType);

    /** Saves a configuration, replacing the type's previous one. */
    void save(ObjectTypeConfiguration configuration);
}
