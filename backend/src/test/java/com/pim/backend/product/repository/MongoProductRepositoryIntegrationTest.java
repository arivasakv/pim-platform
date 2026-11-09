package com.pim.backend.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.product.model.Business;
import com.pim.backend.product.model.Ean;
import com.pim.backend.product.model.Product;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * {@link MongoProductRepository} against the local MongoDB, database {@code pim-test}
 * (src/test/resources/config/application.yaml). Each test starts from an empty collection with the
 * production indexes and a small fixture, and empties it afterwards.
 */
@SpringBootTest
class MongoProductRepositoryIntegrationTest {

    private static final Product LIVERPOOL_LICUADORA =
            new Product("2000010", "7501234567890", "Licuadora", "MARCA A", Business.LIVERPOOL);
    private static final Product LIVERPOOL_SIMILAR_EAN =
            new Product("2000011", "7501234567899", "Licuadora parecida", "MARCA A", Business.LIVERPOOL);
    private static final Product LIVERPOOL_BATIDORA =
            new Product("2000001", "7502000000017", "Batidora", "MARCA B", Business.LIVERPOOL);
    private static final Product SUBURBIA_BATIDORA =
            new Product("2000002", "7502000000017", "Batidora", "MARCA B", Business.SUBURBIA);
    private static final Product MARKETPLACE_AUDIFONOS =
            new Product("2000020", "7501112223334", "Audífonos", "MARCA C", Business.MARKETPLACE);
    private static final Product LIVERPOOL_EAN_WITH_LEADING_ZERO =
            new Product("2000030", "012345678905", "Vaso", "MARCA D", Business.LIVERPOOL);

    private final MongoTemplate mongoTemplate;
    private final MongoProductRepository repository;
    private final MongoProductCatalogLoader loader;

    MongoProductRepositoryIntegrationTest(
            @Autowired MongoTemplate mongoTemplate,
            @Autowired MongoProductRepository repository,
            @Autowired MongoProductCatalogLoader loader) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
        this.loader = loader;
    }

    @BeforeEach
    void startFromTheFixture() {
        mongoTemplate.dropCollection(ProductDocument.class);
        loader.createIndexes();
        loader.loadIfEmpty(List.of(
                LIVERPOOL_LICUADORA,
                LIVERPOOL_SIMILAR_EAN,
                LIVERPOOL_BATIDORA,
                SUBURBIA_BATIDORA,
                MARKETPLACE_AUDIFONOS,
                LIVERPOOL_EAN_WITH_LEADING_ZERO));
    }

    @AfterEach
    void emptyTheCollection() {
        mongoTemplate.dropCollection(ProductDocument.class);
    }

    @Test
    void findsOnlyTheProductWithExactlyThatEan() {
        assertThat(repository.findByEan(Ean.parse("7501234567890"))).containsExactly(LIVERPOOL_LICUADORA);
    }

    @Test
    void keepsLeadingZerosOfTheEan() {
        assertThat(repository.findByEan(Ean.parse("012345678905"))).containsExactly(LIVERPOOL_EAN_WITH_LEADING_ZERO);
    }

    @Test
    void returnsTheSameEanInLiverpoolAndSuburbia() {
        assertThat(repository.findByEan(Ean.parse("7502000000017")))
                .containsExactlyInAnyOrder(LIVERPOOL_BATIDORA, SUBURBIA_BATIDORA);
    }

    @Test
    void returnsMarketplaceProductsSoTheServiceCanExcludeThem() {
        assertThat(repository.findByEan(Ean.parse("7501112223334"))).containsExactly(MARKETPLACE_AUDIFONOS);
    }

    @Test
    void returnsNothingForAnAbsentEan() {
        assertThat(repository.findByEan(Ean.parse("7509999999999"))).isEmpty();
    }

    @Test
    void rejectsTheSameEanTwiceInOneBusiness() {
        Product duplicate = new Product("2000099", "7501234567890", "Otra", "MARCA E", Business.LIVERPOOL);

        assertThatThrownBy(() -> mongoTemplate.insert(ProductDocument.from(duplicate)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void allowsAnEanFromOneBusinessInAnother() {
        Product suburbiaCopy = new Product("2000098", "7501234567890", "Licuadora", "MARCA A", Business.SUBURBIA);

        mongoTemplate.insert(ProductDocument.from(suburbiaCopy));

        assertThat(repository.findByEan(Ean.parse("7501234567890")))
                .containsExactlyInAnyOrder(LIVERPOOL_LICUADORA, suburbiaCopy);
    }

    @Test
    void rejectsARepeatedSku() {
        Product sameSku = new Product("2000010", "7505555555555", "Otra", "MARCA E", Business.SUBURBIA);

        assertThatThrownBy(() -> mongoTemplate.insert(ProductDocument.from(sameSku)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void searchingDoesNotChangeTheCollection() {
        List<ProductDocument> before = mongoTemplate.findAll(ProductDocument.class);

        repository.findByEan(Ean.parse("7502000000017"));
        repository.findByEan(Ean.parse("7509999999999"));

        assertThat(mongoTemplate.findAll(ProductDocument.class)).hasSize(6).containsExactlyInAnyOrderElementsOf(before);
    }
}
