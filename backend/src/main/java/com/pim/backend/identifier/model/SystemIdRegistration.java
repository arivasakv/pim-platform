package com.pim.backend.identifier.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One object that has received a system ID: its type, the key-field values given at registration and how the ID
 * was given. Created once and never changed (FR-007).
 *
 * @param systemId the ID, unique across all object types (FR-001)
 * @param objectType the object's type
 * @param keyFieldValues the declared key fields and their values, in declaration order (FR-009a, FR-013)
 * @param assignment manual or automatic
 * @param registeredAt when the ID was given
 */
public record SystemIdRegistration(
        SystemId systemId,
        ObjectType objectType,
        Map<String, String> keyFieldValues,
        AssignmentMethod assignment,
        Instant registeredAt) {

    public SystemIdRegistration {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(registeredAt, "registeredAt");
        keyFieldValues = Collections.unmodifiableMap(new LinkedHashMap<>(keyFieldValues));
    }
}
