package com.pim.backend.attributegroup.dto;

import com.pim.backend.attributegroup.model.AddAttributesOutcome;
import java.util.List;

/** The group after adding attributes, with what was added and what was already there (contract {@code AddAttributesResult}). */
public record AddAttributesResponse(AttributeGroupResponse group, List<String> added, List<String> alreadyInGroup) {

    public static AddAttributesResponse from(AddAttributesOutcome outcome) {
        return new AddAttributesResponse(
                AttributeGroupResponse.from(outcome.details()), outcome.added(), outcome.alreadyInGroup());
    }
}
