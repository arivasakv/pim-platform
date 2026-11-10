package com.pim.backend.attributegroup.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Attributes to add to a group (contract {@code AddAttributesRequest}): 1 to 100 system IDs. */
public record AddAttributesRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull String> attributeIds) {}
