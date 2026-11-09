package com.pim.backend.product;

import java.util.List;

/**
 * Collection envelope {@code { items, total }} (contract schema {@code ProductCollection};
 * docs/decisions/0001-api-conventions.md).
 */
public record ProductCollectionResponse(List<ProductSummaryResponse> items, int total) {

    static ProductCollectionResponse of(List<ProductSummaryResponse> items) {
        return new ProductCollectionResponse(List.copyOf(items), items.size());
    }
}
