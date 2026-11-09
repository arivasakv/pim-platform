package com.pim.backend.identifier.service;

import com.pim.backend.identifier.model.AssignmentMethod;
import com.pim.backend.identifier.model.AssignmentOutcome;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceExhaustedException;
import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.identifier.model.SystemIdNotFoundException;
import com.pim.backend.identifier.model.SystemIdRegistration;
import com.pim.backend.identifier.repository.ObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.SystemIdRegistry;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Enforces the identifier rules of ERF 1.1 §3.1 (spec FR-001 to FR-013). It is the only entry point for giving a
 * system ID, so every creation path follows the same rules (FR-010). Every operation runs under one lock, so
 * concurrent requests can never register the same ID twice (research R-10, plan decision D-9).
 */
@Service
public class IdentifierService {

    private final SystemIdRegistry registry;
    private final ObjectTypeConfigurationRepository configurations;
    private final Clock clock;
    private final Object lock = new Object();

    private static final int MAX_KEY_FIELD_VALUE_LENGTH = 200;

    public IdentifierService(SystemIdRegistry registry, ObjectTypeConfigurationRepository configurations, Clock clock) {
        this.registry = registry;
        this.configurations = configurations;
        this.clock = clock;
    }

    /**
     * Gives a system ID to a new object of {@code objectType}.
     *
     * @param requestedSystemId manual ID, or {@code null}/blank to generate one from the type's sequence
     * @param keyFieldValues values of the type's declared key fields
     * @param permissions the requesting user's manual-ID permissions
     * @throws SequenceExhaustedException if the type's sequence has no free value left
     */
    public AssignmentResult assign(
            ObjectType objectType,
            String requestedSystemId,
            Map<String, String> keyFieldValues,
            ManualIdPermissions permissions) {
        synchronized (lock) {
            ObjectTypeConfiguration configuration = configurations.find(objectType);
            Map<String, String> values = validatedKeyFieldValues(configuration.keyFields(), keyFieldValues);
            if (requestedSystemId == null || requestedSystemId.isBlank()) {
                return assignAutomatically(configuration, values);
            }
            return assignManually(configuration, requestedSystemId.strip(), values, permissions);
        }
    }

    /**
     * Registers a manual ID (FR-003, FR-004). The permission is checked before the format, so a user without it
     * learns nothing about the value. An existing ID is accepted only for the same object (FR-009); an ID used by
     * any other object, of any type, is rejected (FR-001).
     */
    private AssignmentResult assignManually(
            ObjectTypeConfiguration configuration,
            String requestedSystemId,
            Map<String, String> keyFieldValues,
            ManualIdPermissions permissions) {
        ObjectType objectType = configuration.objectType();
        if (!permissions.allows(objectType)) {
            throw new ManualIdNotPermittedException(objectType, requestedSystemId);
        }
        SystemId systemId = SystemId.of(requestedSystemId);
        Optional<SystemIdRegistration> existing = registry.find(systemId);
        if (existing.isPresent()) {
            if (isSameObject(existing.get(), configuration, keyFieldValues)) {
                return new AssignmentResult(AssignmentOutcome.SAME_OBJECT, existing.get());
            }
            throw new SystemIdInUseException(systemId);
        }
        SystemIdRegistration registration = register(systemId, objectType, keyFieldValues, AssignmentMethod.MANUAL);
        return new AssignmentResult(AssignmentOutcome.ASSIGNED, registration);
    }

    /**
     * Same object = same type and the same values in every declared key field (FR-009a). A type that declares no
     * key fields can never confirm it, so it never matches (plan decision D-8).
     */
    private static boolean isSameObject(
            SystemIdRegistration existing, ObjectTypeConfiguration configuration, Map<String, String> keyFieldValues) {
        return existing.objectType() == configuration.objectType()
                && !configuration.keyFields().isEmpty()
                && existing.keyFieldValues().equals(keyFieldValues);
    }

    /**
     * The given key-field values, trimmed and in declaration order. Exactly the declared fields must be sent, each
     * with 1–200 characters (FR-013; data-model "SystemIdRegistration").
     */
    private static Map<String, String> validatedKeyFieldValues(
            KeyFieldDeclaration declaration, Map<String, String> keyFieldValues) {
        for (String name : keyFieldValues.keySet()) {
            if (!declaration.fieldNames().contains(name)) {
                throw new InvalidIdentifierRequestException(name, IdentifierErrorCode.UNKNOWN_KEY_FIELD, name);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : declaration.fieldNames()) {
            if (!keyFieldValues.containsKey(name)) {
                throw new InvalidIdentifierRequestException(name, IdentifierErrorCode.MISSING_KEY_FIELD, name);
            }
            String value = keyFieldValues.get(name) == null
                    ? ""
                    : keyFieldValues.get(name).strip();
            if (value.isEmpty() || value.length() > MAX_KEY_FIELD_VALUE_LENGTH) {
                throw new InvalidIdentifierRequestException(name, IdentifierErrorCode.INVALID_KEY_FIELD_VALUE, name);
            }
            values.put(name, value);
        }
        return values;
    }

    /**
     * The registration with this ID, compared ignoring letter case.
     *
     * @throws SystemIdNotFoundException if no object has it
     */
    public SystemIdRegistration find(String systemId) {
        synchronized (lock) {
            return parse(systemId).flatMap(registry::find).orElseThrow(() -> new SystemIdNotFoundException(systemId));
        }
    }

    /** The configurations of all object types, in {@link ObjectType} order (FR-006). */
    public List<ObjectTypeConfiguration> findConfigurations() {
        synchronized (lock) {
            return configurations.findAll();
        }
    }

    /** The configuration of one object type. */
    public ObjectTypeConfiguration findConfiguration(ObjectType objectType) {
        synchronized (lock) {
            return configurations.find(objectType);
        }
    }

    /**
     * Replaces a type's sequence and key-field declaration (FR-006, FR-013). It applies to objects created
     * afterwards; registered IDs never change (FR-008).
     */
    public ObjectTypeConfiguration replaceConfiguration(
            ObjectType objectType, SequenceConfiguration sequence, KeyFieldDeclaration keyFields) {
        synchronized (lock) {
            ObjectTypeConfiguration configuration = new ObjectTypeConfiguration(objectType, sequence, keyFields);
            configurations.save(configuration);
            return configuration;
        }
    }

    /** Generates the next free value of the type's sequence, skipping IDs already used (FR-005; D-5). */
    private AssignmentResult assignAutomatically(
            ObjectTypeConfiguration configuration, Map<String, String> keyFieldValues) {
        SequenceConfiguration sequence = configuration.sequence();
        long value = sequence.nextValue();
        SystemId systemId = SystemId.of(sequence.format(value));
        while (registry.contains(systemId)) {
            if (value == SequenceConfiguration.MAX_VALUE) {
                throw new SequenceExhaustedException(configuration.objectType());
            }
            value++;
            systemId = SystemId.of(sequence.format(value));
        }
        SystemIdRegistration registration =
                register(systemId, configuration.objectType(), keyFieldValues, AssignmentMethod.AUTOMATIC);
        configurations.save(configuration.withSequence(
                sequence.withNextValue(Math.min(value + 1, SequenceConfiguration.MAX_VALUE))));
        return new AssignmentResult(AssignmentOutcome.ASSIGNED, registration);
    }

    private SystemIdRegistration register(
            SystemId systemId, ObjectType objectType, Map<String, String> keyFieldValues, AssignmentMethod method) {
        SystemIdRegistration registration =
                new SystemIdRegistration(systemId, objectType, keyFieldValues, method, clock.instant());
        registry.add(registration);
        return registration;
    }

    private static Optional<SystemId> parse(String text) {
        try {
            return Optional.of(SystemId.of(text));
        } catch (InvalidIdentifierRequestException exception) {
            return Optional.empty();
        }
    }
}
