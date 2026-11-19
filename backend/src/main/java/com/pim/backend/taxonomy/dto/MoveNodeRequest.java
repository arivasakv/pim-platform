package com.pim.backend.taxonomy.dto;

import jakarta.validation.constraints.Size;

/**
 * Where to move a node (contract {@code MoveNodeRequest}, feature 011). Unknown fields are ignored.
 *
 * @param parentId the new parent's system ID; null or absent moves the node to the top level, as in {@link
 *     TaxonomyNodeCreateRequest}
 */
public record MoveNodeRequest(@Size(max = 40) String parentId) {}
