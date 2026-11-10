package com.pim.backend.attribute.model;

import java.util.Optional;

/**
 * Regional date formats of a date/time attribute (ERF 1.1 §3.2.1 Fecha y Hora; FR-013, plan decision D-9). The
 * API value and the label are the same text, e.g. {@code DD/MM/AAAA}.
 */
public enum DateFormat {
    DD_MM_YYYY("DD/MM/AAAA"),
    MM_DD_YYYY("MM/DD/AAAA"),
    YYYY_MM_DD("AAAA-MM-DD"),
    DD_MM_YYYY_HH_MM("DD/MM/AAAA HH:mm"),
    YYYY_MM_DD_HH_MM("AAAA-MM-DD HH:mm");

    private final String value;

    DateFormat(String value) {
        this.value = value;
    }

    /** The text used in the API and shown to users. */
    public String value() {
        return value;
    }

    /** The format with exactly this API value, or empty when it is unknown. */
    public static Optional<DateFormat> fromValue(String text) {
        for (DateFormat format : values()) {
            if (format.value.equals(text)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
