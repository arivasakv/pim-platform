package com.pim.backend.product.repository;

import com.pim.backend.product.model.Business;
import com.pim.backend.product.model.Product;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A product as stored in the MongoDB collection {@value #COLLECTION} (data-model.md, "Stored product
 * document"). Package-private: Spring Data types never leave the repository package (constitution XII).
 *
 * @param id MongoDB identifier, generated on insert and never exposed
 * @param ean normalized EAN digits, matched by equality only
 * @param business enum name: {@code LIVERPOOL}, {@code SUBURBIA} or {@code MARKETPLACE}
 */
@Document(ProductDocument.COLLECTION)
record ProductDocument(@Id String id, String sku, String ean, String title, String brand, Business business) {

    static final String COLLECTION = "products";

    static ProductDocument from(Product product) {
        return new ProductDocument(
                null, product.sku(), product.ean(), product.title(), product.brand(), product.business());
    }

    Product toProduct() {
        return new Product(sku, ean, title, brand, business);
    }
}
