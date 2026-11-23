package com.pim.backend.attribute.model;

import static com.pim.backend.attribute.model.TextPropertiesTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Currency properties (FR-014; plan decision D-10). */
class CurrencyPropertiesTest {

    @Test
    void acceptsAnIsoCodeWithTwoDecimalsByDefault() {
        CurrencyProperties properties = CurrencyProperties.of("MXN", null);

        assertThat(properties.currencyCode()).isEqualTo("MXN");
        assertThat(properties.decimals()).isEqualTo(2);
        assertThat(properties.dataType()).isEqualTo(AttributeDataType.CURRENCY);
        assertThat(properties.currencySymbol()).isEqualTo("$");
        // The defect fixed on 2026-11-23 (FR-014 amendment): a euro attribute showed "EUR".
        assertThat(CurrencyProperties.of("EUR", null).currencySymbol()).isEqualTo("€");
    }

    @Test
    void requiresAKnownIsoCode() {
        assertRejected(
                () -> CurrencyProperties.of(null, null),
                "currencyProperties.currencyCode",
                AttributeErrorCode.REQUIRED);
        assertRejected(
                () -> CurrencyProperties.of("XYZ", null),
                "currencyProperties.currencyCode",
                AttributeErrorCode.UNKNOWN_CURRENCY);
        assertRejected(
                () -> CurrencyProperties.of("mxn", null),
                "currencyProperties.currencyCode",
                AttributeErrorCode.UNKNOWN_CURRENCY);
    }

    @Test
    void decimalsGoFromZeroToFour() {
        assertThat(CurrencyProperties.of("USD", 0).decimals()).isZero();
        assertThat(CurrencyProperties.of("EUR", 4).decimals()).isEqualTo(4);
        assertRejected(
                () -> CurrencyProperties.of("MXN", 5),
                "currencyProperties.decimals",
                AttributeErrorCode.INVALID_DECIMALS);
        assertRejected(
                () -> CurrencyProperties.of("MXN", -1),
                "currencyProperties.decimals",
                AttributeErrorCode.INVALID_DECIMALS);
    }
}
