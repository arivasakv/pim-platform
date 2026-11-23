package com.pim.backend.attribute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One choice offered by the creation form.
 *
 * @param code value sent back in requests
 * @param label Spanish label
 * @param symbol currency symbol, only for the currencies of a currency attribute (feature 004 FR-014 amendment of
 *     2026-11-23); omitted otherwise
 */
public record OptionResponse(
        String code,
        String label,
        @JsonInclude(JsonInclude.Include.NON_NULL) String symbol) {

    /** A choice without a symbol. */
    public OptionResponse(String code, String label) {
        this(code, label, null);
    }
}
