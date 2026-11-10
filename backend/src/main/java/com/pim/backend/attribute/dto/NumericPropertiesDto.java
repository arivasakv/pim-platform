package com.pim.backend.attribute.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Numeric properties in requests and responses (FR-010; plan decision D-8). */
public record NumericPropertiesDto(
        @NotNull String numberFormat, Integer precision, BigDecimal minimum, BigDecimal maximum) {}
