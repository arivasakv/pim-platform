package com.pim.backend.attribute.dto;

import jakarta.validation.constraints.NotNull;

/** Reference properties in requests and responses (FR-015; plan decision D-11). */
public record ReferencePropertiesDto(@NotNull String targetObjectType, String cardinality) {}
