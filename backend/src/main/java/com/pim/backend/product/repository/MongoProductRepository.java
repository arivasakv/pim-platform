package com.pim.backend.product.repository;

import com.pim.backend.product.model.Ean;
import com.pim.backend.product.model.Product;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Reads the product catalog from MongoDB (research R-17). Read-only: a search never changes the catalog
 * (spec FR-012). It returns every business for an EAN; the search service applies the business rules. If
 * MongoDB cannot be reached, Spring's {@code DataAccessResourceFailureException} propagates; there is no
 * fallback data.
 */
@Repository
public class MongoProductRepository implements ProductRepository {

    private final ProductDocumentRepository documents;

    MongoProductRepository(ProductDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public List<Product> findByEan(Ean ean) {
        return documents.findByEan(ean.value()).stream()
                .map(ProductDocument::toProduct)
                .toList();
    }
}
