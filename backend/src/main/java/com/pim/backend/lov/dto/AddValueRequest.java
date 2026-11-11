package com.pim.backend.lov.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/lists-of-values/{listId}/values} (contract {@code AddValueRequest}). Sizes here only
 * stop oversized requests; the value rules are checked by the model.
 *
 * @param id absent or blank = generated
 */
public record AddValueRequest(
        @Size(max = 100) String id,
        @NotNull @Size(max = 500) String label,
        @Size(max = 500) String equivalence) {}
