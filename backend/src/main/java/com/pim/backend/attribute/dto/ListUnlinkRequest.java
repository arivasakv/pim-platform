package com.pim.backend.attribute.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/v1/attributes/{systemId}/list-unlink} (feature 014 contract {@code ListUnlinkRequest}): the
 * new data type, the properties object of that type in feature 004's shapes, and the token of the review. A display
 * name or system ID sent here is ignored.
 */
public record ListUnlinkRequest(
        @NotNull String dataType,
        @Valid TextPropertiesDto textProperties,
        @Valid NumericPropertiesDto numericProperties,
        @Valid ListOfValuesPropertiesDto listOfValuesProperties,
        @Valid DateTimePropertiesDto dateTimeProperties,
        @Valid CurrencyPropertiesDto currencyProperties,
        @Valid ReferencePropertiesDto referenceProperties,
        String reviewToken) {

    /** The same properties as a creation request, so feature 004's mapping and rules apply unchanged. */
    public AttributeCreationRequest asCreationRequest() {
        return new AttributeCreationRequest(
                null,
                "-",
                dataType,
                textProperties,
                numericProperties,
                listOfValuesProperties,
                dateTimeProperties,
                currencyProperties,
                referenceProperties);
    }
}
