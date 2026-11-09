package com.pim.backend.identifier.dto;

import com.pim.backend.identifier.model.SystemIdRegistration;
import java.time.Instant;
import java.util.Map;

/**
 * A registered system ID as returned by the API.
 *
 * @param systemId the ID as it was given
 * @param objectType object-type code
 * @param keyFieldValues key-field values given at registration
 * @param assignment {@code MANUAL} or {@code AUTOMATIC}
 * @param registeredAt when the ID was given
 */
public record SystemIdRegistrationResponse(
        String systemId,
        String objectType,
        Map<String, String> keyFieldValues,
        String assignment,
        Instant registeredAt) {

    public static SystemIdRegistrationResponse from(SystemIdRegistration registration) {
        return new SystemIdRegistrationResponse(
                registration.systemId().value(),
                registration.objectType().name(),
                registration.keyFieldValues(),
                registration.assignment().name(),
                registration.registeredAt());
    }
}
