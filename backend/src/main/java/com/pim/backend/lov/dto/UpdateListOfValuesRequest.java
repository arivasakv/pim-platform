package com.pim.backend.lov.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/v1/lists-of-values/{listId}} (contract {@code UpdateListOfValuesRequest}). */
public record UpdateListOfValuesRequest(
        @NotNull @Size(max = 200) String name, @NotNull Boolean equivalencesEnabled) {}
