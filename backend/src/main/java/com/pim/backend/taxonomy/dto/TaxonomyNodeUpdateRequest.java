package com.pim.backend.taxonomy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A node's new name and description (contract {@code TaxonomyNodeUpdateRequest}); the parent never changes. */
public record TaxonomyNodeUpdateRequest(
        @NotNull @Size(max = 200) String name,
        @Size(max = 1000) String description) {}
