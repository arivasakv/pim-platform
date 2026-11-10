package com.pim.backend.attribute.model;

import static com.pim.backend.attribute.model.TextPropertiesTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Date/time properties (FR-013; plan decision D-9). */
class DateTimePropertiesTest {

    @Test
    void requiresADateFormat() {
        assertThat(new DateTimeProperties(DateFormat.DD_MM_YYYY).dateFormat().value())
                .isEqualTo("DD/MM/AAAA");
        assertThat(new DateTimeProperties(DateFormat.DD_MM_YYYY).dataType()).isEqualTo(AttributeDataType.DATE_TIME);
        assertRejected(
                () -> new DateTimeProperties(null), "dateTimeProperties.dateFormat", AttributeErrorCode.REQUIRED);
    }

    @Test
    void offersTheFiveFormatsOfTheDecision() {
        assertThat(DateFormat.values())
                .extracting(DateFormat::value)
                .containsExactly("DD/MM/AAAA", "MM/DD/AAAA", "AAAA-MM-DD", "DD/MM/AAAA HH:mm", "AAAA-MM-DD HH:mm");
        assertThat(DateFormat.fromValue("YYYY")).isEmpty();
    }
}
