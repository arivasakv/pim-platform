package com.pim.backend.attribute.dto;

/** Text properties in requests and responses (FR-008, FR-009; plan decisions D-6, D-7). */
public record TextPropertiesDto(
        Integer minLength, Integer maxLength, String caseFormat, String captureMask, Boolean richText) {}
