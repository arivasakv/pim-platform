package com.pim.backend.identifier.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Body of {@code POST /api/v1/system-ids}: give a system ID to a new object (FR-012).
 *
 * @param objectType object-type code, e.g. {@code ATTRIBUTE}
 * @param systemId manual ID (FR-003); missing or blank means "generate one" (FR-005, spec A-004)
 * @param keyFieldValues one value per key field declared for the type (FR-009a, FR-013); {@code {}} when none
 */
public record SystemIdAssignmentRequest(
        @NotNull String objectType,
        @Size(max = 60) String systemId,
        @NotNull @Size(max = 20) Map<String, String> keyFieldValues) {}
