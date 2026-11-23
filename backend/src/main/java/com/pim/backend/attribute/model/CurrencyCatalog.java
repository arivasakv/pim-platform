package com.pim.backend.attribute.model;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The currencies offered for a currency attribute and their symbols (feature 004 FR-014, amended 2026-11-23).
 *
 * <p>The list holds every ISO 4217 currency in current use by a country, as known to the runtime, with MXN, USD and
 * EUR first and the rest by Spanish name. The symbol is the one used in Mexico (es-MX) when it differs from the code;
 * otherwise the one most countries using the currency show locally (USD → $, EUR → €); otherwise the code. Both are
 * worked out once, on first use.
 */
public final class CurrencyCatalog {

    /**
     * One currency of the selector.
     *
     * @param code ISO 4217 code, e.g. {@code MXN}
     * @param name Spanish name, starting with a capital letter
     * @param symbol symbol shown with values, e.g. {@code $}
     */
    public record CurrencyEntry(String code, String name, String symbol) {}

    private static final Locale SPANISH_MX = Locale.of("es", "MX");
    private static final List<String> FIRST = List.of("MXN", "USD", "EUR");

    private CurrencyCatalog() {}

    /** The currencies of the selector, in display order. */
    public static List<CurrencyEntry> inUse() {
        return Data.IN_USE;
    }

    /** The symbol for a currency code known to the runtime (see the class comment for the rule). */
    public static String symbolOf(String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        String mexican = currency.getSymbol(SPANISH_MX);
        if (!mexican.equals(currencyCode)) {
            return mexican;
        }
        return Optional.ofNullable(Data.LOCAL_SYMBOLS.get(currencyCode)).orElse(currencyCode);
    }

    /** Built on first use: reading every locale takes a moment. */
    private static final class Data {
        static final Map<String, String> LOCAL_SYMBOLS = localSymbols();
        static final List<CurrencyEntry> IN_USE = inUseCurrencies();

        /** For each currency, the symbol most of the countries using it show locally, unless that is the code. */
        private static Map<String, String> localSymbols() {
            Map<String, Map<String, Integer>> votes = new HashMap<>();
            for (Locale locale : Locale.getAvailableLocales()) {
                Currency currency = currencyOf(locale.getCountry());
                if (currency != null) {
                    votes.computeIfAbsent(currency.getCurrencyCode(), code -> new HashMap<>())
                            .merge(currency.getSymbol(locale), 1, Integer::sum);
                }
            }
            Map<String, String> symbols = new HashMap<>();
            votes.forEach((code, counts) -> counts.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                            .thenComparing(entry -> -entry.getKey().length())
                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .filter(best -> !best.equals(code))
                    .ifPresent(best -> symbols.put(code, best)));
            return Map.copyOf(symbols);
        }

        private static List<CurrencyEntry> inUseCurrencies() {
            TreeSet<String> codes = new TreeSet<>();
            for (String country : Locale.getISOCountries()) {
                Currency currency = currencyOf(country);
                if (currency != null) {
                    codes.add(currency.getCurrencyCode());
                }
            }
            Collator spanish = Collator.getInstance(SPANISH_MX);
            List<CurrencyEntry> entries = new ArrayList<>();
            for (String code : codes) {
                entries.add(new CurrencyEntry(code, nameOf(code), symbolOf(code)));
            }
            entries.sort(Comparator.comparingInt((CurrencyEntry entry) -> rankOf(entry.code()))
                    .thenComparing(CurrencyEntry::name, spanish));
            return List.copyOf(entries);
        }

        private static int rankOf(String code) {
            int index = FIRST.indexOf(code);
            return index < 0 ? FIRST.size() : index;
        }

        private static String nameOf(String code) {
            String name = Currency.getInstance(code).getDisplayName(SPANISH_MX);
            return name.isEmpty() ? code : name.substring(0, 1).toUpperCase(SPANISH_MX) + name.substring(1);
        }

        /** The currency of a two-letter country code, or {@code null} when it has none. */
        private static Currency currencyOf(String country) {
            if (country.length() != 2) {
                return null;
            }
            try {
                return Currency.getInstance(Locale.of("", country));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
