package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeProperties;
import com.pim.backend.attribute.model.AttributeScope;
import com.pim.backend.attribute.model.Cardinality;
import com.pim.backend.attribute.model.CaseFormat;
import com.pim.backend.attribute.model.CurrencyProperties;
import com.pim.backend.attribute.model.DateFormat;
import com.pim.backend.attribute.model.DateTimeProperties;
import com.pim.backend.attribute.model.DisplayName;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.NumberFormat;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.ReferenceProperties;
import com.pim.backend.attribute.model.ReferenceTargetType;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.model.TextProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An attribute as stored in the collection {@value #COLLECTION} (ADR 0003). Package-private: Spring Data types never
 * leave the repository package (constitution XII). Exactly one properties sub-document is present, matching {@code
 * dataType}; {@code BOOLEAN} has none.
 *
 * @param id the system ID upper-cased, so two IDs differing only in letter case collide on {@code _id}
 * @param displayNameKey the display name lower-cased ({@code Locale.ROOT}); unique index {@value #DISPLAY_NAME_INDEX}
 * @param updatedAt last change (feature 007); {@code null} for attributes never edited, including every document
 *     written before feature 007. A {@code description} stored before 2026-11-20 is no longer mapped and stays unused
 *     in the document (feature 004 FR-003 amendment).
 */
@Document(AttributeDocument.COLLECTION)
record AttributeDocument(
        @Id String id,
        String systemId,
        String displayName,
        String displayNameKey,
        AttributeDataType dataType,
        AttributeScope scope,
        Text textProperties,
        Numeric numericProperties,
        ListOfValues listOfValuesProperties,
        DateTime dateTimeProperties,
        Currency currencyProperties,
        Reference referenceProperties,
        Instant createdAt,
        Instant updatedAt) {

    static final String COLLECTION = "attributes";
    static final String DISPLAY_NAME_KEY = "displayNameKey";
    static final String DISPLAY_NAME_INDEX = "attribute_display_name_unique";

    record Text(Integer minLength, Integer maxLength, CaseFormat caseFormat, String captureMask, boolean richText) {}

    /** Limits are kept as plain strings so their exact value and scale survive (no binary rounding). */
    record Numeric(NumberFormat numberFormat, Integer precision, String minimum, String maximum) {}

    record ListOfValues(SelectionMode selectionMode, String listOfValuesId) {}

    record DateTime(DateFormat dateFormat) {}

    record Currency(String currencyCode, int decimals) {}

    record Reference(ReferenceTargetType targetObjectType, Cardinality cardinality) {}

    static String key(String systemId) {
        return systemId.toUpperCase(Locale.ROOT);
    }

    static AttributeDocument from(Attribute attribute) {
        AttributeProperties properties = attribute.properties();
        return new AttributeDocument(
                key(attribute.systemId()),
                attribute.systemId(),
                attribute.displayName().value(),
                attribute.displayName().comparisonKey(),
                attribute.dataType(),
                attribute.scope(),
                properties instanceof TextProperties text
                        ? new Text(
                                text.minLength(),
                                text.maxLength(),
                                text.caseFormat(),
                                text.captureMask(),
                                text.richText())
                        : null,
                properties instanceof NumericProperties numeric
                        ? new Numeric(
                                numeric.numberFormat(),
                                numeric.precision(),
                                plain(numeric.minimum()),
                                plain(numeric.maximum()))
                        : null,
                properties instanceof ListOfValuesProperties list
                        ? new ListOfValues(list.selectionMode(), list.listOfValuesId())
                        : null,
                properties instanceof DateTimeProperties dateTime ? new DateTime(dateTime.dateFormat()) : null,
                properties instanceof CurrencyProperties currency
                        ? new Currency(currency.currencyCode(), currency.decimals())
                        : null,
                properties instanceof ReferenceProperties reference
                        ? new Reference(reference.targetObjectType(), reference.cardinality())
                        : null,
                attribute.createdAt(),
                attribute.updatedAt().orElse(null));
    }

    Attribute toAttribute() {
        return new Attribute(
                systemId,
                DisplayName.of(displayName),
                dataType,
                scope,
                toProperties(),
                createdAt,
                Optional.ofNullable(updatedAt));
    }

    private AttributeProperties toProperties() {
        return switch (dataType) {
            case TEXT ->
                new TextProperties(
                        textProperties.minLength(),
                        textProperties.maxLength(),
                        textProperties.caseFormat(),
                        textProperties.captureMask(),
                        textProperties.richText());
            case NUMERIC ->
                new NumericProperties(
                        numericProperties.numberFormat(),
                        numericProperties.precision(),
                        decimal(numericProperties.minimum()),
                        decimal(numericProperties.maximum()));
            case BOOLEAN -> null;
            case LIST_OF_VALUES ->
                new ListOfValuesProperties(
                        listOfValuesProperties.selectionMode(), listOfValuesProperties.listOfValuesId());
            case DATE_TIME -> new DateTimeProperties(dateTimeProperties.dateFormat());
            case CURRENCY -> new CurrencyProperties(currencyProperties.currencyCode(), currencyProperties.decimals());
            case REFERENCE ->
                new ReferenceProperties(referenceProperties.targetObjectType(), referenceProperties.cardinality());
        };
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
