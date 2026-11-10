package com.pim.backend.attribute.dto;

import java.util.List;

/**
 * Every attribute, in the project-wide collection envelope (docs/decisions/0001-api-conventions.md; contract {@code
 * AttributeCollection}, v1.1.0).
 *
 * @param items the attributes, sorted by display name ignoring letter case
 * @param total the number of items
 */
public record AttributeCollectionResponse(List<AttributeResponse> items, int total) {

    public static AttributeCollectionResponse of(List<AttributeResponse> items) {
        return new AttributeCollectionResponse(List.copyOf(items), items.size());
    }
}
