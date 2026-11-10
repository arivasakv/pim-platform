package com.pim.backend.attribute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeProperties;
import com.pim.backend.attribute.model.CurrencyProperties;
import com.pim.backend.attribute.model.DateTimeProperties;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.ReferenceProperties;
import com.pim.backend.attribute.model.TextProperties;
import java.time.Instant;

/**
 * An attribute as returned by the API (contract {@code Attribute}). Only the properties object of its data type is
 * present; {@code BOOLEAN} has none.
 */
public record AttributeResponse(
        String systemId,
        String displayName,
        String description,
        String dataType,
        String scope,
        @JsonInclude(JsonInclude.Include.NON_NULL) TextPropertiesDto textProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) NumericPropertiesDto numericProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) ListOfValuesPropertiesDto listOfValuesProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) DateTimePropertiesDto dateTimeProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) CurrencyPropertiesDto currencyProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReferencePropertiesDto referenceProperties,
        Instant createdAt) {

    public static AttributeResponse from(Attribute attribute) {
        AttributeProperties properties = attribute.properties();
        return new AttributeResponse(
                attribute.systemId(),
                attribute.displayName().value(),
                attribute.description().orElse(null),
                attribute.dataType().name(),
                attribute.scope().name(),
                properties instanceof TextProperties text ? text(text) : null,
                properties instanceof NumericProperties numeric ? numeric(numeric) : null,
                properties instanceof ListOfValuesProperties listOfValues
                        ? new ListOfValuesPropertiesDto(
                                listOfValues.selectionMode().name(), listOfValues.listOfValuesId())
                        : null,
                properties instanceof DateTimeProperties dateTime
                        ? new DateTimePropertiesDto(dateTime.dateFormat().value())
                        : null,
                properties instanceof CurrencyProperties currency
                        ? new CurrencyPropertiesDto(
                                currency.currencyCode(), currency.decimals(), currency.currencySymbol())
                        : null,
                properties instanceof ReferenceProperties reference
                        ? new ReferencePropertiesDto(
                                reference.targetObjectType().name(),
                                reference.cardinality().name())
                        : null,
                attribute.createdAt());
    }

    private static TextPropertiesDto text(TextProperties text) {
        return new TextPropertiesDto(
                text.minLength(),
                text.maxLength(),
                text.caseFormat() == null ? null : text.caseFormat().name(),
                text.captureMask(),
                text.richText());
    }

    private static NumericPropertiesDto numeric(NumericProperties numeric) {
        return new NumericPropertiesDto(
                numeric.numberFormat().name(), numeric.precision(), numeric.minimum(), numeric.maximum());
    }
}
