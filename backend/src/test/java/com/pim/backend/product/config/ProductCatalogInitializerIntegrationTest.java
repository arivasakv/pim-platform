package com.pim.backend.product.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.product.repository.MongoProductCatalogLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;

/**
 * Startup preparation of the {@code products} collection in the local MongoDB, database {@code pim-test}:
 * indexes, sample data loaded once, and no duplicates on restart (research R-18, R-19).
 */
@SpringBootTest
class ProductCatalogInitializerIntegrationTest {

    private static final String PRODUCTS = "products";
    private static final int SAMPLE_CATALOG_SIZE = 531;

    private final MongoTemplate mongoTemplate;
    private final ProductCatalogInitializer initializer;
    private final MongoProductCatalogLoader loader;

    ProductCatalogInitializerIntegrationTest(
            @Autowired MongoTemplate mongoTemplate,
            @Autowired ProductCatalogInitializer initializer,
            @Autowired MongoProductCatalogLoader loader) {
        this.mongoTemplate = mongoTemplate;
        this.initializer = initializer;
        this.loader = loader;
    }

    @BeforeEach
    void startFromAnEmptyDatabase() {
        mongoTemplate.dropCollection(PRODUCTS);
    }

    @AfterEach
    void leaveTheSampleCatalogLoaded() {
        mongoTemplate.dropCollection(PRODUCTS);
        initializer.initialize();
    }

    @Test
    void createsTheIndexesAndLoadsTheSampleCatalog() {
        initializer.initialize();

        assertThat(productCount()).isEqualTo(SAMPLE_CATALOG_SIZE);
        assertThat(uniqueIndexKeys())
                .containsEntry(MongoProductCatalogLoader.EAN_BUSINESS_INDEX, List.of("ean", "business"))
                .containsEntry(MongoProductCatalogLoader.SKU_INDEX, List.of("sku"));
    }

    @Test
    void doesNotInsertDuplicatesWhenTheApplicationStartsAgain() {
        initializer.initialize();
        initializer.initialize();

        assertThat(productCount()).isEqualTo(SAMPLE_CATALOG_SIZE);
        assertThat(mongoTemplate.getCollection(PRODUCTS).countDocuments(new Document("ean", "7502000000017")))
                .isEqualTo(2);
    }

    @Test
    void onlyCreatesTheIndexesWhenSampleDataLoadingIsDisabled() {
        new ProductCatalogInitializer(loader, false).initialize();

        assertThat(productCount()).isZero();
        assertThat(uniqueIndexKeys())
                .containsKeys(MongoProductCatalogLoader.EAN_BUSINESS_INDEX, MongoProductCatalogLoader.SKU_INDEX);
    }

    private long productCount() {
        return mongoTemplate.getCollection(PRODUCTS).countDocuments();
    }

    /** Unique indexes of the collection: name → indexed field names, in key order. */
    private Map<String, List<String>> uniqueIndexKeys() {
        Map<String, List<String>> keys = new HashMap<>();
        for (IndexInfo index : mongoTemplate.indexOps(PRODUCTS).getIndexInfo()) {
            if (index.isUnique()) {
                keys.put(
                        index.getName(),
                        index.getIndexFields().stream().map(IndexField::getKey).toList());
            }
        }
        return keys;
    }
}
