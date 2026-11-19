package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.MoveOutcome;

/** The moved node and how many nodes moved with it (contract {@code MoveNodeResult}, feature 011). */
public record MoveNodeResponse(TaxonomyNodeResponse node, int movedNodeCount) {

    public static MoveNodeResponse from(MoveOutcome outcome) {
        return new MoveNodeResponse(TaxonomyNodeResponse.from(outcome.details()), outcome.movedNodeCount());
    }
}
