package com.pim.backend.product.config;

import com.pim.backend.product.repository.MongoProductCatalogLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Prepares the product catalog when the application starts (research R-19): always creates the MongoDB
 * indexes, and loads {@link SampleProductCatalog} into an empty collection when
 * {@code pim.catalog.seed-sample-data} is true. If MongoDB cannot be reached, startup fails; the
 * application never runs on fallback data.
 */
@Component
public class ProductCatalogInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogInitializer.class);

    private final MongoProductCatalogLoader loader;
    private final boolean seedSampleData;

    ProductCatalogInitializer(
            MongoProductCatalogLoader loader, @Value("${pim.catalog.seed-sample-data:true}") boolean seedSampleData) {
        this.loader = loader;
        this.seedSampleData = seedSampleData;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    /** Creates the indexes and, if enabled, loads the sample catalog into an empty collection. */
    public void initialize() {
        loader.createIndexes();
        if (!seedSampleData) {
            log.info("Product catalog: indexes ready; sample data loading is disabled");
            return;
        }
        int inserted = loader.loadIfEmpty(SampleProductCatalog.create());
        if (inserted > 0) {
            log.info("Product catalog: loaded {} sample products into MongoDB", inserted);
        } else {
            log.info("Product catalog: collection already has data; nothing loaded");
        }
    }
}
