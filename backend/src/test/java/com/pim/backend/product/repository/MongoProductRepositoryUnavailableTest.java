package com.pim.backend.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.pim.backend.product.model.Ean;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

/**
 * When MongoDB cannot be reached, a search fails fast with Spring's
 * {@link DataAccessResourceFailureException} (turned into 503 by {@code common.DataAccessExceptionHandler})
 * and returns no fallback data. Uses an address where nothing listens; needs no running MongoDB.
 */
class MongoProductRepositoryUnavailableTest {

    private final MongoClient client = MongoClients.create("mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=500");

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void failsWithAResourceFailureWhenMongoDbIsUnreachable() {
        MongoTemplate template = new MongoTemplate(client, "pim-test");
        ProductDocumentRepository documents =
                new MongoRepositoryFactory(template).getRepository(ProductDocumentRepository.class);
        MongoProductRepository repository = new MongoProductRepository(documents);

        long start = System.nanoTime();
        assertThatThrownBy(() -> repository.findByEan(Ean.parse("7501234567890")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }
}
