package com.pim.backend.attribute.dto;

import jakarta.validation.constraints.NotNull;

/** Date/time properties in requests and responses (FR-013; plan decision D-9). */
public record DateTimePropertiesDto(@NotNull String dateFormat) {}
