package com.pim.backend.taxonomy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A node to create (contract {@code TaxonomyNodeCreateRequest}). The size limits only cap the request; the business
 * limits (name 1–80, description up to 500, after trimming) are checked by the model. Unknown fields are ignored.
 *
 * @param parentId the parent's system ID; null or absent for a top-level node
 */
public record TaxonomyNodeCreateRequest(
        @NotNull @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Size(max = 40) String parentId) {}
