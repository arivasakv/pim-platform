package com.pim.backend.identifier.dto;

import java.util.List;

/**
 * The configurations of all object types, in the project's collection envelope (ADR 0001).
 *
 * @param items the configurations, in the order of ERF 1.1 §3.1
 * @param total number of items
 */
public record ObjectTypeConfigurationCollectionResponse(List<ObjectTypeConfigurationResponse> items, int total) {

    public static ObjectTypeConfigurationCollectionResponse of(List<ObjectTypeConfigurationResponse> items) {
        return new ObjectTypeConfigurationCollectionResponse(List.copyOf(items), items.size());
    }
}
