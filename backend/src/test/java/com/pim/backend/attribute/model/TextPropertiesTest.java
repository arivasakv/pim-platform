package com.pim.backend.attribute.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Text properties (FR-008, FR-009; plan decisions D-6, D-7). */
class TextPropertiesTest {

    @Test
    void allPropertiesAreOptionalAndRichTextDefaultsToFalse() {
        TextProperties properties = new TextProperties(null, null, null, null, false);

        assertThat(properties.dataType()).isEqualTo(AttributeDataType.TEXT);
        assertThat(properties.richText()).isFalse();
    }

    @Test
    void minimumLengthIsAWholeNumberFromZeroToOneHundredThousand() {
        assertThat(new TextProperties(0, null, null, null, false).minLength()).isZero();
        assertThat(new TextProperties(100_000, null, null, null, false).minLength())
                .isEqualTo(100_000);
        assertRejected(
                () -> new TextProperties(-1, null, null, null, false),
                "textProperties.minLength",
                AttributeErrorCode.INVALID_LENGTH);
        assertRejected(
                () -> new TextProperties(100_001, null, null, null, false),
                "textProperties.minLength",
                AttributeErrorCode.INVALID_LENGTH);
    }

    @Test
    void maximumLengthIsAWholeNumberFromOneToOneHundredThousand() {
        assertRejected(
                () -> new TextProperties(null, 0, null, null, false),
                "textProperties.maxLength",
                AttributeErrorCode.INVALID_LENGTH);
        assertRejected(
                () -> new TextProperties(null, 100_001, null, null, false),
                "textProperties.maxLength",
                AttributeErrorCode.INVALID_LENGTH);
    }

    @Test
    void minimumMayNotExceedMaximum() {
        assertThat(new TextProperties(5, 5, null, null, false).maxLength()).isEqualTo(5);
        assertRejected(
                () -> new TextProperties(10, 5, null, null, false),
                "textProperties.maxLength",
                AttributeErrorCode.INVALID_LENGTH_RANGE);
    }

    @Test
    void captureMaskMustBeAValidRegularExpressionOfUpToFiveHundredCharacters() {
        assertThat(new TextProperties(null, null, null, "^[A-Z ]+$", false).captureMask())
                .isEqualTo("^[A-Z ]+$");
        assertRejected(
                () -> new TextProperties(null, null, null, "[a-", false),
                "textProperties.captureMask",
                AttributeErrorCode.INVALID_CAPTURE_MASK);
        assertRejected(
                () -> new TextProperties(null, null, null, "a".repeat(501), false),
                "textProperties.captureMask",
                AttributeErrorCode.INVALID_CAPTURE_MASK);
        assertRejected(
                () -> new TextProperties(null, null, null, "", false),
                "textProperties.captureMask",
                AttributeErrorCode.INVALID_CAPTURE_MASK);
    }

    @Test
    void caseFormatAndCaptureMaskAreSeparateAndAllowedOnRichText() {
        TextProperties properties = new TextProperties(null, null, CaseFormat.UPPERCASE, "^[A-Z]+$", true);

        assertThat(properties.caseFormat()).isEqualTo(CaseFormat.UPPERCASE);
        assertThat(properties.captureMask()).isEqualTo("^[A-Z]+$");
        assertThat(properties.richText()).isTrue();
    }

    static void assertRejected(Runnable action, String field, AttributeErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(InvalidAttributeException.class, exception -> {
            assertThat(exception.field()).isEqualTo(field);
            assertThat(exception.code()).isEqualTo(code);
        });
    }
}
