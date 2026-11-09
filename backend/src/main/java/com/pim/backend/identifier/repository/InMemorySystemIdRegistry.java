package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdRegistration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link SystemIdRegistry} (constitution IX; plan decision D-1): registrations are lost when the backend
 * stops. Not thread-safe by itself: {@code IdentifierService} calls it under one lock (research R-10).
 */
@Repository
public class InMemorySystemIdRegistry implements SystemIdRegistry {

    private final Map<SystemId, SystemIdRegistration> registrations = new HashMap<>();

    @Override
    public Optional<SystemIdRegistration> find(SystemId systemId) {
        return Optional.ofNullable(registrations.get(systemId));
    }

    @Override
    public boolean contains(SystemId systemId) {
        return registrations.containsKey(systemId);
    }

    @Override
    public void add(SystemIdRegistration registration) {
        if (registrations.putIfAbsent(registration.systemId(), registration) != null) {
            throw new IllegalStateException("System ID already registered: " + registration.systemId());
        }
    }

    @Override
    public int count() {
        return registrations.size();
    }
}
