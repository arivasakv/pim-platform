package com.pim.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SampleProductCatalogTest {

    private final List<Product> catalog = SampleProductCatalog.create();

    @Test
    void containsAtLeast500ProductsAndExactly531() {
        assertThat(catalog).hasSizeGreaterThanOrEqualTo(500).hasSize(531);
    }

    @Test
    void containsTheSixScenarioProductsExactly() {
        assertThat(catalog)
                .contains(
                        new Product(
                                "1000001",
                                "7501234567890",
                                "Licuadora 10 velocidades 600 W",
                                "MARCA DEMO",
                                Business.LIVERPOOL),
                        new Product(
                                "1000002", "7502000000017", "Batidora de mano 300 W", "HOGAR DEMO", Business.LIVERPOOL),
                        new Product(
                                "1000003", "7502000000017", "Batidora de mano 300 W", "HOGAR DEMO", Business.SUBURBIA),
                        new Product(
                                "1000004",
                                "7501112223334",
                                "Audífonos inalámbricos",
                                "AUDIO DEMO",
                                Business.MARKETPLACE),
                        new Product("1000005", "12345670", "Termo de acero 500 ml", "CASA DEMO", Business.LIVERPOOL),
                        new Product(
                                "1000006",
                                "17501234567893",
                                "Caja con 6 vasos de vidrio",
                                "CASA DEMO",
                                Business.SUBURBIA));
    }

    @Test
    void doesNotContainTheAbsentScenarioEan() {
        assertThat(catalog).noneMatch(product -> product.ean().equals("7509999999999"));
    }

    @Test
    void marketplaceOnlyEanBelongsOnlyToMarketplace() {
        assertThat(catalog)
                .filteredOn(product -> product.ean().equals("7501112223334"))
                .extracting(Product::business)
                .containsOnly(Business.MARKETPLACE);
    }

    @Test
    void noEanRepeatsWithinTheSameBusiness() {
        Map<String, Long> countsByEanAndBusiness = catalog.stream()
                .collect(Collectors.groupingBy(
                        product -> product.ean() + "|" + product.business(), Collectors.counting()));
        assertThat(countsByEanAndBusiness.values()).allMatch(count -> count == 1);
    }

    @Test
    void everySkuIsUniqueAndHasSevenDigits() {
        Set<String> skus = new HashSet<>();
        assertThat(catalog).allSatisfy(product -> {
            assertThat(product.sku()).matches("^[0-9]{7}$");
            assertThat(skus.add(product.sku()))
                    .as("unique SKU %s", product.sku())
                    .isTrue();
        });
    }

    @Test
    void titlesAndBrandsRespectTheirLengthLimits() {
        assertThat(catalog).allSatisfy(product -> {
            assertThat(product.title()).isNotBlank().hasSizeLessThanOrEqualTo(120);
            assertThat(product.brand()).isNotBlank().hasSizeLessThanOrEqualTo(60);
        });
    }
}
