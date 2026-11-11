package com.pim.backend.lov.model;

import java.util.Set;

/**
 * Generated value IDs (research R-07): the list's counter as a number padded with zeros to at least 4 digits
 * ({@code 0001}, …, {@code 9999}, {@code 10000}). An ID already taken by hand is skipped; the counter never goes back.
 */
public final class GeneratedValueIds {

    private GeneratedValueIds() {}

    /** The ID for this counter value. */
    public static String format(long number) {
        return String.format("%04d", number);
    }

    /**
     * The first counter value, from {@code next} on, whose ID is not taken.
     *
     * @param takenKeys comparison keys ({@link ValueId#key()}) of the IDs already in the list
     */
    public static long firstFree(long next, Set<String> takenKeys) {
        long number = next;
        while (takenKeys.contains(ValueId.keyOf(format(number)))) {
            number++;
        }
        return number;
    }
}
