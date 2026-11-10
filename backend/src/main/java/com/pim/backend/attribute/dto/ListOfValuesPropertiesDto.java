package com.pim.backend.attribute.dto;

/**
 * List-of-values properties in requests and responses (FR-012; plan decision D-12). Neither field is required by
 * the schema: the existence of lists is checked first (data-model "ListOfValuesProperties").
 */
public record ListOfValuesPropertiesDto(String selectionMode, String listOfValuesId) {}
