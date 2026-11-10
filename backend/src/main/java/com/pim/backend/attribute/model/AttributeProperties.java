package com.pim.backend.attribute.model;

/**
 * The type-specific properties of an attribute (FR-008 to FR-015). Exactly one record per data type implements it;
 * {@code BOOLEAN} has none.
 */
public sealed interface AttributeProperties
        permits TextProperties,
                NumericProperties,
                ListOfValuesProperties,
                DateTimeProperties,
                CurrencyProperties,
                ReferenceProperties {

    /** The data type these properties belong to. */
    AttributeDataType dataType();
}
