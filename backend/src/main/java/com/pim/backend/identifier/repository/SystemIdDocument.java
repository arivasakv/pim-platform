package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.AssignmentMethod;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdRegistration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A system ID registration as stored in the collection {@value #COLLECTION} (ADR 0003). Package-private: Spring Data
 * types never leave the repository package (constitution XII).
 *
 * @param id the ID upper-cased, so two IDs differing only in letter case collide on {@code _id}
 * @param systemId the ID as given
 * @param keyFieldValues the key fields in declaration order (a list keeps the order explicit)
 */
@Document(SystemIdDocument.COLLECTION)
record SystemIdDocument(
        @Id String id,
        String systemId,
        ObjectType objectType,
        List<KeyFieldValue> keyFieldValues,
        AssignmentMethod assignment,
        Instant registeredAt) {

    static final String COLLECTION = "system_ids";

    /** One key field and its value. */
    record KeyFieldValue(String name, String value) {}

    static String key(SystemId systemId) {
        return systemId.value().toUpperCase(Locale.ROOT);
    }

    static SystemIdDocument from(SystemIdRegistration registration) {
        return new SystemIdDocument(
                key(registration.systemId()),
                registration.systemId().value(),
                registration.objectType(),
                registration.keyFieldValues().entrySet().stream()
                        .map(entry -> new KeyFieldValue(entry.getKey(), entry.getValue()))
                        .toList(),
                registration.assignment(),
                registration.registeredAt());
    }

    SystemIdRegistration toRegistration() {
        Map<String, String> values = new LinkedHashMap<>();
        if (keyFieldValues != null) {
            keyFieldValues.forEach(field -> values.put(field.name(), field.value()));
        }
        return new SystemIdRegistration(SystemId.of(systemId), objectType, values, assignment, registeredAt);
    }
}
