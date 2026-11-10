package com.pim.backend.attributegroup.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A group to create, or a group's new name and description (contract {@code AttributeGroupRequest}). The size limits
 * here only cap the request; the business limits (name 1–80, description up to 500, after trimming) are checked by
 * the model (plan decision D-5). Unknown fields are ignored.
 */
public record AttributeGroupRequest(
        @NotNull @Size(max = 200) String name,
        @Size(max = 1000) String description) {}
