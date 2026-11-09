package com.pim.backend.identifier.dto;

import com.pim.backend.identifier.model.SequenceConfiguration;
import jakarta.validation.constraints.NotNull;

/**
 * An object type's ID sequence in requests and responses (FR-006; plan decision D-2).
 *
 * @param kind {@code NUMERIC} or {@code ALPHANUMERIC}
 * @param prefix 1–10 capital letters for {@code ALPHANUMERIC}; absent or {@code null} for {@code NUMERIC}
 * @param minimumDigits zero padding, 1–18
 * @param nextValue next number to try, 1 to 999,999,999,999,999,999
 */
public record SequenceDto(
        @NotNull String kind,
        String prefix,
        @NotNull Integer minimumDigits,
        @NotNull Long nextValue) {

    public static SequenceDto from(SequenceConfiguration sequence) {
        return new SequenceDto(
                sequence.kind().name(), sequence.prefix(), sequence.minimumDigits(), sequence.nextValue());
    }
}
