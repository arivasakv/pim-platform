package com.pim.backend.attribute.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Currency properties in requests and responses (FR-014; plan decision D-10). {@code currencySymbol} is filled in
 * responses only and ignored in requests.
 */
public record CurrencyPropertiesDto(@NotNull String currencyCode, Integer decimals, String currencySymbol) {}
