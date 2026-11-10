package com.pim.backend.attribute.model;

import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Properties of a currency attribute (ERF 1.1 §3.2.1 Moneda; FR-014; plan decision D-10). The currency is fixed per
 * attribute; its symbol is derived from the ISO 4217 code.
 *
 * @param currencyCode ISO 4217 code known to the runtime, e.g. {@code MXN}
 * @param decimals 0–4
 */
public record CurrencyProperties(String currencyCode, int decimals) implements AttributeProperties {

    /** Decimals used when none are given. */
    public static final int DEFAULT_DECIMALS = 2;

    private static final Pattern ISO_CODE = Pattern.compile("[A-Z]{3}");
    private static final Locale SYMBOL_LOCALE = Locale.of("es", "MX");

    public CurrencyProperties {
        if (currencyCode == null) {
            throw new InvalidAttributeException("currencyProperties.currencyCode", AttributeErrorCode.REQUIRED);
        }
        if (!isKnownCurrency(currencyCode)) {
            throw new InvalidAttributeException(
                    "currencyProperties.currencyCode", AttributeErrorCode.UNKNOWN_CURRENCY, currencyCode);
        }
        if (decimals < 0 || decimals > 4) {
            throw new InvalidAttributeException("currencyProperties.decimals", AttributeErrorCode.INVALID_DECIMALS);
        }
    }

    /** Currency properties with {@value #DEFAULT_DECIMALS} decimals when {@code decimals} is absent. */
    public static CurrencyProperties of(String currencyCode, Integer decimals) {
        return new CurrencyProperties(currencyCode, decimals == null ? DEFAULT_DECIMALS : decimals);
    }

    /** The currency symbol shown with values, e.g. {@code $} for {@code MXN}. */
    public String currencySymbol() {
        return Currency.getInstance(currencyCode).getSymbol(SYMBOL_LOCALE);
    }

    @Override
    public AttributeDataType dataType() {
        return AttributeDataType.CURRENCY;
    }

    private static boolean isKnownCurrency(String code) {
        if (!ISO_CODE.matcher(code).matches()) {
            return false;
        }
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
