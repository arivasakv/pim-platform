package com.pim.backend.attribute.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/attributes} (contract {@code AttributeCreationRequest}). Only the properties object
 * matching {@code dataType} is read; the others are ignored (spec A-004).
 */
public record AttributeCreationRequest(
        @Size(max = 60) String systemId,
        @NotNull @Size(max = 200) String displayName,
        @Size(max = 2000) String description,
        @NotNull String dataType,
        @Valid TextPropertiesDto textProperties,
        @Valid NumericPropertiesDto numericProperties,
        @Valid ListOfValuesPropertiesDto listOfValuesProperties,
        @Valid DateTimePropertiesDto dateTimeProperties,
        @Valid CurrencyPropertiesDto currencyProperties,
        @Valid ReferencePropertiesDto referenceProperties) {}
