package com.pim.backend.attribute.controller;

import com.pim.backend.attribute.dto.AttributeCreationRequest;
import com.pim.backend.attribute.dto.CurrencyPropertiesDto;
import com.pim.backend.attribute.dto.DateTimePropertiesDto;
import com.pim.backend.attribute.dto.ListOfValuesPropertiesDto;
import com.pim.backend.attribute.dto.NumericPropertiesDto;
import com.pim.backend.attribute.dto.ReferencePropertiesDto;
import com.pim.backend.attribute.dto.TextPropertiesDto;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.AttributeProperties;
import com.pim.backend.attribute.model.Cardinality;
import com.pim.backend.attribute.model.CaseFormat;
import com.pim.backend.attribute.model.CurrencyProperties;
import com.pim.backend.attribute.model.DateFormat;
import com.pim.backend.attribute.model.DateTimeProperties;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.NumberFormat;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.ReferenceProperties;
import com.pim.backend.attribute.model.ReferenceTargetType;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.model.TextProperties;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns the request's properties object of the chosen data type into model properties (HTTP mapping only; the
 * rules live in the model records). Unknown option texts are request-format errors (400 {@code UNKNOWN_*}).
 * Properties objects of other types are ignored (spec A-004).
 */
final class AttributePropertiesMapper {

    private AttributePropertiesMapper() {}

    /** The model properties of {@code dataType}; {@code null} for {@code BOOLEAN}. */
    static AttributeProperties fromRequest(AttributeDataType dataType, AttributeCreationRequest request) {
        return switch (dataType) {
            case BOOLEAN -> null;
            case TEXT -> text(request.textProperties());
            case NUMERIC -> numeric(required(request.numericProperties(), "numericProperties"));
            case DATE_TIME -> dateTime(required(request.dateTimeProperties(), "dateTimeProperties"));
            case CURRENCY -> currency(required(request.currencyProperties(), "currencyProperties"));
            case LIST_OF_VALUES -> listOfValues(request.listOfValuesProperties());
            case REFERENCE -> reference(required(request.referenceProperties(), "referenceProperties"));
        };
    }

    /**
     * A missing object gives empty properties, so the service can first answer "no lists exist yet" (422) before
     * asking for the missing fields (data-model "ListOfValuesProperties").
     */
    private static ListOfValuesProperties listOfValues(ListOfValuesPropertiesDto dto) {
        if (dto == null) {
            return new ListOfValuesProperties(null, null);
        }
        return new ListOfValuesProperties(
                option(
                        dto.selectionMode(),
                        SelectionMode::fromCode,
                        "listOfValuesProperties.selectionMode",
                        AttributeErrorCode.UNKNOWN_SELECTION_MODE),
                dto.listOfValuesId());
    }

    /** Text properties are all optional, so a missing object means "no properties". */
    private static TextProperties text(TextPropertiesDto dto) {
        if (dto == null) {
            return new TextProperties(null, null, null, null, false);
        }
        return new TextProperties(
                dto.minLength(),
                dto.maxLength(),
                option(
                        dto.caseFormat(),
                        CaseFormat::fromCode,
                        "textProperties.caseFormat",
                        AttributeErrorCode.UNKNOWN_CASE_FORMAT),
                dto.captureMask(),
                Boolean.TRUE.equals(dto.richText()));
    }

    private static NumericProperties numeric(NumericPropertiesDto dto) {
        return new NumericProperties(
                option(
                        dto.numberFormat(),
                        NumberFormat::fromCode,
                        "numericProperties.numberFormat",
                        AttributeErrorCode.UNKNOWN_NUMBER_FORMAT),
                dto.precision(),
                dto.minimum(),
                dto.maximum());
    }

    private static DateTimeProperties dateTime(DateTimePropertiesDto dto) {
        return new DateTimeProperties(option(
                dto.dateFormat(),
                DateFormat::fromValue,
                "dateTimeProperties.dateFormat",
                AttributeErrorCode.UNKNOWN_DATE_FORMAT));
    }

    private static CurrencyProperties currency(CurrencyPropertiesDto dto) {
        return CurrencyProperties.of(dto.currencyCode(), dto.decimals());
    }

    private static ReferenceProperties reference(ReferencePropertiesDto dto) {
        return ReferenceProperties.of(
                option(
                        dto.targetObjectType(),
                        ReferenceTargetType::fromCode,
                        "referenceProperties.targetObjectType",
                        AttributeErrorCode.UNKNOWN_TARGET_OBJECT_TYPE),
                option(
                        dto.cardinality(),
                        Cardinality::fromCode,
                        "referenceProperties.cardinality",
                        AttributeErrorCode.UNKNOWN_CARDINALITY));
    }

    /** {@code null} stays {@code null} (the model decides whether it is required); unknown text is an error. */
    static <T> T option(String text, Function<String, Optional<T>> lookup, String field, AttributeErrorCode code) {
        if (text == null) {
            return null;
        }
        return lookup.apply(text).orElseThrow(() -> new InvalidAttributeException(field, code, text));
    }

    static <T> T required(T dto, String field) {
        if (dto == null) {
            throw new InvalidAttributeException(field, AttributeErrorCode.REQUIRED);
        }
        return dto;
    }
}
