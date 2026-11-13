package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.AddAttributesOutcome;
import java.util.List;

/** The node after assigning attributes, with what was added and what was already there (contract {@code AssignAttributesResult}). */
public record AssignAttributesResponse(TaxonomyNodeResponse node, List<String> added, List<String> alreadyAssigned) {

    public static AssignAttributesResponse from(AddAttributesOutcome outcome) {
        return new AssignAttributesResponse(
                TaxonomyNodeResponse.from(outcome.details()), outcome.added(), outcome.alreadyAssigned());
    }
}
