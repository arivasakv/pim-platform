package com.pim.backend.attribute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * List-of-values properties in responses (feature 004 FR-012). Feature 007 adds the linked list's name, read only
 * and absent when the list is not found (research R-07).
 */
public record ListOfValuesPropertiesResponse(
        String selectionMode,
        String listOfValuesId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String listOfValuesName) {}
