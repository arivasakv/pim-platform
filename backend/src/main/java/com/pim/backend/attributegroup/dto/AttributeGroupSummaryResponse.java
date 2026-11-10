package com.pim.backend.attributegroup.dto;

import com.pim.backend.attributegroup.model.AttributeGroup;

/** A group in the list, without its members (contract {@code AttributeGroupSummary}). */
public record AttributeGroupSummaryResponse(String id, String name, String description, int attributeCount) {

    public static AttributeGroupSummaryResponse from(AttributeGroup group) {
        return new AttributeGroupSummaryResponse(
                group.id(),
                group.name().value(),
                group.description().orElse(null),
                group.attributeIds().size());
    }
}
