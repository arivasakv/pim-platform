package com.pim.backend.taxonomy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Attributes to assign directly to a node (contract {@code AssignAttributesRequest}): 1 to 100 system IDs. */
public record AssignAttributesRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Size(max = 40) String> attributeIds) {}
