package com.pim.backend.product.model;

/**
 * An existing product in the catalog (data-model.md, "Product").
 *
 * @param sku seven-digit identifier, unique across the catalog
 * @param ean normalized EAN (8, 12, 13 or 14 digits)
 * @param title product title, at most 120 characters
 * @param brand brand name, at most 60 characters
 * @param business business line the product belongs to
 */
public record Product(String sku, String ean, String title, String brand, Business business) {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_BRAND_LENGTH = 60;

    public Product {
        requireText(sku, "sku");
        requireText(ean, "ean");
        requireText(title, "title");
        requireText(brand, "brand");
        if (business == null) {
            throw new IllegalArgumentException("business is required");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        if (brand.length() > MAX_BRAND_LENGTH) {
            throw new IllegalArgumentException("brand must be at most " + MAX_BRAND_LENGTH + " characters");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
