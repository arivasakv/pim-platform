package com.pim.backend.attribute.model;

/**
 * Properties of a date/time attribute (ERF 1.1 §3.2.1 Fecha y Hora; FR-013; plan decision D-9). Validations between
 * dates are business rules (§3.2.4), not attribute properties.
 *
 * @param dateFormat regional format; required
 */
public record DateTimeProperties(DateFormat dateFormat) implements AttributeProperties {

    public DateTimeProperties {
        if (dateFormat == null) {
            throw new InvalidAttributeException("dateTimeProperties.dateFormat", AttributeErrorCode.REQUIRED);
        }
    }

    @Override
    public AttributeDataType dataType() {
        return AttributeDataType.DATE_TIME;
    }
}
