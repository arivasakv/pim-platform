package com.pim.backend.product.repository;

import com.pim.backend.product.model.Product;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Prepares the {@code products} collection: creates its indexes and loads catalog data into an empty
 * collection (research R-18, R-19). Kept apart from {@link ProductRepository} so the search path stays
 * read-only (spec FR-012).
 */
@Component
public class MongoProductCatalogLoader {

    /** Serves the EAN lookup; an EAN may repeat across businesses but only once per business. */
    public static final String EAN_BUSINESS_INDEX = "ean_business_unique";

    /** SKUs are unique across the whole catalog (data-model.md). */
    public static final String SKU_INDEX = "sku_unique";

    private final MongoTemplate mongoTemplate;

    MongoProductCatalogLoader(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the collection's indexes if they do not exist yet. Safe to call on every start. */
    public void createIndexes() {
        IndexOperations indexes = mongoTemplate.indexOps(ProductDocument.class);
        indexes.createIndex(new Index()
                .on("ean", Sort.Direction.ASC)
                .on("business", Sort.Direction.ASC)
                .unique()
                .named(EAN_BUSINESS_INDEX));
        indexes.createIndex(new Index().on("sku", Sort.Direction.ASC).unique().named(SKU_INDEX));
    }

    /**
     * Inserts {@code products} only if the collection is empty, so restarts never add duplicates.
     *
     * @return the number of products inserted (0 when the collection already had data)
     */
    public int loadIfEmpty(List<Product> products) {
        if (mongoTemplate.count(new Query(), ProductDocument.class) > 0) {
            return 0;
        }
        return mongoTemplate
                .insertAll(products.stream().map(ProductDocument::from).toList())
                .size();
    }
}
