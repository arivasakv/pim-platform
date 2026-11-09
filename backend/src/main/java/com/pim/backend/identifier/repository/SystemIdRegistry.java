package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdRegistration;
import java.util.Optional;

/**
 * Stores the registered system IDs. IDs are compared ignoring letter case ({@link SystemId}). Callers check for an
 * existing ID before adding one; this interface holds no business rules (constitution XII).
 */
public interface SystemIdRegistry {

    Optional<SystemIdRegistration> find(SystemId systemId);

    boolean contains(SystemId systemId);

    /**
     * Adds a registration.
     *
     * @throws IllegalStateException if its ID is already registered
     */
    void add(SystemIdRegistration registration);

    int count();
}
