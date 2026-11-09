package com.pim.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSearchServiceTest {

    private static final Product LIVERPOOL_LICUADORA =
            new Product("2000010", "7501234567890", "Licuadora", "MARCA A", Business.LIVERPOOL);
    private static final Product LIVERPOOL_SIMILAR_PREFIX =
            new Product("2000011", "7501234567899", "Licuadora parecida", "MARCA A", Business.LIVERPOOL);
    private static final Product SUBURBIA_BATIDORA =
            new Product("2000002", "7502000000017", "Batidora", "MARCA B", Business.SUBURBIA);
    private static final Product LIVERPOOL_BATIDORA =
            new Product("2000001", "7502000000017", "Batidora", "MARCA B", Business.LIVERPOOL);
    private static final Product MARKETPLACE_AUDIFONOS =
            new Product("2000020", "7501112223334", "Audífonos", "MARCA C", Business.MARKETPLACE);

    private final List<Product> fixture = List.of(
            LIVERPOOL_LICUADORA,
            LIVERPOOL_SIMILAR_PREFIX,
            SUBURBIA_BATIDORA,
            LIVERPOOL_BATIDORA,
            MARKETPLACE_AUDIFONOS);
    private final InMemoryProductRepository repository = new InMemoryProductRepository(fixture);
    private final ProductSearchService service = new ProductSearchService(repository);

    @Test
    void returnsOnlyTheProductWithExactlyThatEan() {
        assertThat(service.findExistingMercaderiasProducts(Ean.parse("7501234567890")))
                .containsExactly(LIVERPOOL_LICUADORA);
    }

    @Test
    void returnsTheSameEanInLiverpoolAndSuburbiaWithLiverpoolFirst() {
        assertThat(service.findExistingMercaderiasProducts(Ean.parse("7502000000017")))
                .containsExactly(LIVERPOOL_BATIDORA, SUBURBIA_BATIDORA);
    }

    @Test
    void neverReturnsMarketplaceProducts() {
        assertThat(service.findExistingMercaderiasProducts(Ean.parse("7501112223334")))
                .isEmpty();
    }

    @Test
    void returnsNothingForAnAbsentEan() {
        assertThat(service.findExistingMercaderiasProducts(Ean.parse("7509999999999")))
                .isEmpty();
    }

    @Test
    void searchingDoesNotChangeTheCatalog() {
        service.findExistingMercaderiasProducts(Ean.parse("7502000000017"));
        service.findExistingMercaderiasProducts(Ean.parse("7501112223334"));

        assertThat(repository.findByEan(Ean.parse("7502000000017")))
                .containsExactlyInAnyOrder(LIVERPOOL_BATIDORA, SUBURBIA_BATIDORA);
        assertThat(repository.findByEan(Ean.parse("7501112223334"))).containsExactly(MARKETPLACE_AUDIFONOS);
    }

    @Test
    void rejectsACatalogWithTheSameEanTwiceInOneBusiness() {
        Product duplicate = new Product("2000099", "7501234567890", "Otra", "MARCA D", Business.LIVERPOOL);

        assertThatThrownBy(() -> new InMemoryProductRepository(List.of(LIVERPOOL_LICUADORA, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("7501234567890")
                .hasMessageContaining("LIVERPOOL");
    }
}
