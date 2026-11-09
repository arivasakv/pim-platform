package com.pim.backend.product;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the in-memory product catalog once, when the application context starts (research R-03). */
@Configuration
public class ProductCatalogConfiguration {

    @Bean
    ProductRepository productRepository() {
        return new InMemoryProductRepository(SampleProductCatalog.create());
    }
}
