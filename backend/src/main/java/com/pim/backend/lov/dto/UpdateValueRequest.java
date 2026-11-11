package com.pim.backend.lov.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/lists-of-values/{listId}/values/{valueId}} (contract {@code UpdateValueRequest}).
 *
 * @param equivalence replaces the equivalence; {@code null} or blank removes it
 */
public record UpdateValueRequest(
        @NotNull @Size(max = 500) String label,
        @Size(max = 500) String equivalence) {}
