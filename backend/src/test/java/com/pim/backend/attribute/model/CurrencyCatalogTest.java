package com.pim.backend.attribute.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.CurrencyCatalog.CurrencyEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The currency selector and its symbols (feature 004 FR-014, amended 2026-11-23). */
class CurrencyCatalogTest {

    @Test
    void offersTheCurrenciesInUseWithMxnUsdAndEurFirst() {
        List<CurrencyEntry> currencies = CurrencyCatalog.inUse();

        assertThat(currencies.subList(0, 3))
                .containsExactly(
                        new CurrencyEntry("MXN", "Peso mexicano", "$"),
                        new CurrencyEntry("USD", "Dólar estadounidense", "$"),
                        new CurrencyEntry("EUR", "Euro", "€"));
        assertThat(currencies).hasSizeGreaterThan(100);
        assertThat(currencies).extracting(CurrencyEntry::code).doesNotHaveDuplicates();
        assertThat(currencies)
                .extracting(CurrencyEntry::code)
                .contains("GBP", "JPY", "BRL", "CHF", "XAF")
                .doesNotContain("XAU", "XXX", "XTS");
    }

    @Test
    void sortsTheRestBySpanishName() {
        List<String> rest = CurrencyCatalog.inUse()
                .subList(3, CurrencyCatalog.inUse().size())
                .stream()
                .map(CurrencyEntry::name)
                .toList();
        java.text.Collator spanish = java.text.Collator.getInstance(java.util.Locale.of("es", "MX"));

        assertThat(rest).isSortedAccordingTo(spanish::compare);
    }

    @Test
    void usesTheMexicanSymbolThenTheLocalOneThenTheCode() {
        assertThat(CurrencyCatalog.symbolOf("MXN")).isEqualTo("$");
        assertThat(CurrencyCatalog.symbolOf("USD")).isEqualTo("$");
        assertThat(CurrencyCatalog.symbolOf("EUR")).isEqualTo("€");
        assertThat(CurrencyCatalog.symbolOf("GBP")).isEqualTo("£");
        assertThat(CurrencyCatalog.symbolOf("BRL")).isEqualTo("R$");
        assertThat(CurrencyCatalog.symbolOf("CHF")).isEqualTo("CHF");
        assertThat(CurrencyCatalog.symbolOf("XAU")).isEqualTo("XAU");
    }
}
