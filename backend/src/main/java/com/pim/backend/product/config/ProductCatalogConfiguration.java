package com.pim.backend.product.config;

import com.pim.backend.product.repository.InMemoryProductRepository;
import com.pim.backend.product.repository.ProductRepository;
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
