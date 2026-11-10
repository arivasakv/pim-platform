package com.pim.backend.attributegroup.dto;

import com.pim.backend.attributegroup.model.AttributeGroup;
import java.util.List;

/**
 * The groups, in the project-wide collection envelope (docs/decisions/0001-api-conventions.md; contract {@code
 * AttributeGroupCollection}).
 */
public record AttributeGroupCollectionResponse(List<AttributeGroupSummaryResponse> items, int total) {

    public static AttributeGroupCollectionResponse of(List<AttributeGroup> groups) {
        List<AttributeGroupSummaryResponse> items =
                groups.stream().map(AttributeGroupSummaryResponse::from).toList();
        return new AttributeGroupCollectionResponse(items, items.size());
    }
}
