package com.pim.backend.attribute.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDetails;
import com.pim.backend.attribute.model.AttributeProperties;
import com.pim.backend.attribute.model.CurrencyProperties;
import com.pim.backend.attribute.model.DateTimeProperties;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.ReferenceProperties;
import com.pim.backend.attribute.model.TextProperties;
import java.time.Instant;
import java.util.List;

/**
 * An attribute as returned by the API (contract {@code Attribute}). Only the properties object of its data type is
 * present; {@code BOOLEAN} has none. Feature 007 (contract v1.2.0) adds {@code groups}, {@code updatedAt} (absent
 * until the first edit) and the linked list's name. Contract v2.0.0 removes {@code description}.
 */
public record AttributeResponse(
        String systemId,
        String displayName,
        String dataType,
        String scope,
        @JsonInclude(JsonInclude.Include.NON_NULL) TextPropertiesDto textProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) NumericPropertiesDto numericProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) ListOfValuesPropertiesResponse listOfValuesProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) DateTimePropertiesDto dateTimeProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) CurrencyPropertiesDto currencyProperties,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReferencePropertiesDto referenceProperties,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt,
        List<GroupReferenceResponse> groups) {

    public static AttributeResponse from(AttributeDetails details) {
        Attribute attribute = details.attribute();
        AttributeProperties properties = attribute.properties();
        return new AttributeResponse(
                attribute.systemId(),
                attribute.displayName().value(),
                attribute.dataType().name(),
                attribute.scope().name(),
                properties instanceof TextProperties text ? text(text) : null,
                properties instanceof NumericProperties numeric ? numeric(numeric) : null,
                properties instanceof ListOfValuesProperties listOfValues
                        ? new ListOfValuesPropertiesResponse(
                                listOfValues.selectionMode().name(),
                                listOfValues.listOfValuesId(),
                                details.listOfValuesName().orElse(null))
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
                attribute.createdAt(),
                attribute.updatedAt().orElse(null),
                details.groups().stream().map(GroupReferenceResponse::from).toList());
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
