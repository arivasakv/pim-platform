package com.pim.backend.attribute.dto;

/**
 * One choice offered by the creation form.
 *
 * @param code value sent back in requests
 * @param label Spanish label
 */
public record OptionResponse(String code, String label) {}
