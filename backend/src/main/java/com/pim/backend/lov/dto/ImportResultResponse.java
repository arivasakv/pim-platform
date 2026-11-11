package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.ImportOutcome;

/** Result of a CSV import (contract {@code ImportResult}); {@code total} is the number of values afterwards. */
public record ImportResultResponse(int added, int updated, int total) {

    public static ImportResultResponse from(ImportOutcome outcome) {
        return new ImportResultResponse(
                outcome.added(), outcome.updated(), outcome.list().values().size());
    }
}
