package com.pim.backend.product;

/** One product in a search result (contract schema {@code ProductSummary}). */
public record ProductSummaryResponse(String sku, String ean, String title, String brand, Business business) {

    static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.sku(), product.ean(), product.title(), product.brand(), product.business());
    }
}
