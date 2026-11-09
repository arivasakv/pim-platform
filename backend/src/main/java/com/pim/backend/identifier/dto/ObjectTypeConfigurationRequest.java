package com.pim.backend.identifier.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code PUT /api/v1/object-types/{objectType}}: the type's new configuration (FR-006, FR-013).
 *
 * @param sequence the ID sequence
 * @param keyFields the key-field names, possibly empty
 */
public record ObjectTypeConfigurationRequest(
        @NotNull @Valid SequenceDto sequence, @NotNull List<String> keyFields) {}
