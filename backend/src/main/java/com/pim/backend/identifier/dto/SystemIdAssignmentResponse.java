package com.pim.backend.identifier.dto;

import com.pim.backend.identifier.model.AssignmentResult;

/**
 * Response of {@code POST /api/v1/system-ids}.
 *
 * @param outcome {@code ASSIGNED} (new ID) or {@code SAME_OBJECT} (FR-009a)
 * @param registration the new or existing registration
 */
public record SystemIdAssignmentResponse(String outcome, SystemIdRegistrationResponse registration) {

    public static SystemIdAssignmentResponse from(AssignmentResult result) {
        return new SystemIdAssignmentResponse(
                result.outcome().name(), SystemIdRegistrationResponse.from(result.registration()));
    }
}
