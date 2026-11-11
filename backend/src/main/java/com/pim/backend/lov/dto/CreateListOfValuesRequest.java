package com.pim.backend.lov.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/lists-of-values} (contract {@code CreateListOfValuesRequest}). Sizes here only stop
 * oversized requests; the name rules (trimmed, 1–80) are checked by the model.
 *
 * @param systemId optional manual system ID (feature 003 rules); absent = generated
 */
public record CreateListOfValuesRequest(
        @NotNull @Size(max = 200) String name,
        @Size(max = 40) String systemId) {}
