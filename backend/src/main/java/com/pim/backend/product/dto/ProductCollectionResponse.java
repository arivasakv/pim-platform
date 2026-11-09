package com.pim.backend.product.dto;

import java.util.List;

/**
 * Collection envelope {@code { items, total }} (contract schema {@code ProductCollection};
 * docs/decisions/0001-api-conventions.md).
 */
public record ProductCollectionResponse(List<ProductSummaryResponse> items, int total) {

    public ProductCollectionResponse {
        items = List.copyOf(items);
    }
}
