package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.DisplayName;
import java.util.List;
import java.util.Optional;

/** Stores attribute definitions. Holds no business rules (constitution XII); the service checks before saving. */
public interface AttributeRepository {

    /** The attribute with this system ID, compared ignoring letter case. */
    Optional<Attribute> findBySystemId(String systemId);

    /** Whether an attribute has this display name, compared ignoring letter case. */
    boolean existsWithDisplayName(DisplayName displayName);

    /**
     * Whether an attribute other than {@code exceptSystemId} has this display name, both compared ignoring letter case
     * (feature 007 FR-008).
     */
    boolean existsWithDisplayName(DisplayName displayName, String exceptSystemId);

    /**
     * Saves a new attribute.
     *
     * @throws IllegalStateException if its system ID or display name is already stored
     */
    void save(Attribute attribute);

    /**
     * Replaces the stored attribute with the same system ID (feature 007).
     *
     * @throws IllegalStateException if another attribute already has its display name
     */
    void update(Attribute attribute);

    /** Deletes the attribute with this system ID, compared ignoring letter case; nothing happens if none has it. */
    void delete(String systemId);

    /** Every attribute, sorted by display name ignoring letter case (feature 005 plan decision D-3). */
    List<Attribute> findAll();

    int count();
}
