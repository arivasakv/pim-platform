package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.DisplayName;
import java.util.Optional;

/** Stores attribute definitions. Holds no business rules (constitution XII); the service checks before saving. */
public interface AttributeRepository {

    /** The attribute with this system ID, compared ignoring letter case. */
    Optional<Attribute> findBySystemId(String systemId);

    /** Whether an attribute has this display name, compared ignoring letter case. */
    boolean existsWithDisplayName(DisplayName displayName);

    /**
     * Saves a new attribute.
     *
     * @throws IllegalStateException if its system ID or display name is already stored
     */
    void save(Attribute attribute);

    int count();
}
