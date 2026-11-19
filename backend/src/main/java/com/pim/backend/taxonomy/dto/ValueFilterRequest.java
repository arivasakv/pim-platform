package com.pim.backend.taxonomy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The values to include in a node's filter (contract {@code ValueFilterRequest}, feature 012). Repeats are ignored;
 * the model checks the business limit after removing them.
 */
public record ValueFilterRequest(
        @NotNull @Size(min = 1, max = 50_000) List<@NotNull @Size(max = 40) String> valueIds) {}
