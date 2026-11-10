package com.pim.backend.attribute.model;

import java.util.Optional;

/** Exact-match lookup of an enum constant by its code (its name), shared by the attribute enums. */
final class Codes {

    private Codes() {}

    static <E extends Enum<E>> Optional<E> find(E[] values, String code) {
        for (E value : values) {
            if (value.name().equals(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
