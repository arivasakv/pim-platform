package com.pim.backend.attribute.dto;

import java.util.List;

/** The data types in the project's collection envelope (ADR 0001). */
public record AttributeTypeCollectionResponse(List<AttributeTypeResponse> items, int total) {

    public static AttributeTypeCollectionResponse of(List<AttributeTypeResponse> items) {
        return new AttributeTypeCollectionResponse(List.copyOf(items), items.size());
    }
}
