package com.pim.backend.product.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.product.config.ProductCatalogInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API integration tests for {@code GET /api/v1/products?ean=}, run against the sample catalog stored in the
 * local MongoDB, database {@code pim-test} (src/test/resources/config/application.yaml). Expectations follow specs/002-existing-product-search/contracts/product-search.openapi.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    private static final String PRODUCTS = "/api/v1/products";

    private final MockMvc mockMvc;

    private final ProductCatalogInitializer catalogInitializer;

    ProductControllerIntegrationTest(
            @Autowired MockMvc mockMvc, @Autowired ProductCatalogInitializer catalogInitializer) {
        this.mockMvc = mockMvc;
        this.catalogInitializer = catalogInitializer;
    }

    /** Other test classes empty the shared test database; reload the sample catalog if it is empty. */
    @BeforeEach
    void loadTheSampleCatalog() {
        catalogInitializer.initialize();
    }

    @Test
    void returnsTheLiverpoolProductWithThatEan() throws Exception {
        search("7501234567890")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("1000001"))
                .andExpect(jsonPath("$.items[0].ean").value("7501234567890"))
                .andExpect(jsonPath("$.items[0].title").value("Licuadora 10 velocidades 600 W"))
                .andExpect(jsonPath("$.items[0].brand").value("MARCA DEMO"))
                .andExpect(jsonPath("$.items[0].business").value("LIVERPOOL"));
    }

    @Test
    void returnsTheSameEanInLiverpoolAndThenSuburbia() throws Exception {
        search("7502000000017")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].business", contains("LIVERPOOL", "SUBURBIA")));
    }

    @Test
    void returnsAnEmptyCollectionForAnAbsentEan() throws Exception {
        expectEmpty(search("7509999999999"));
    }

    @Test
    void neverReturnsAMarketplaceProduct() throws Exception {
        expectEmpty(search("7501112223334"));
    }

    @Test
    void removesSpacesAndDashesBeforeSearching() throws Exception {
        search("750 1234-567890")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("1000001"));
    }

    @ParameterizedTest
    @CsvSource({"12345670, 1000005", "17501234567893, 1000006"})
    void acceptsEan8AndGtin14(String ean, String expectedSku) throws Exception {
        search(ean)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sku").value(expectedSku));
    }

    @Test
    void rejectsAMissingEanParameter() throws Exception {
        expectProblem(mockMvc.perform(get(PRODUCTS)), "REQUIRED");
    }

    @ParameterizedTest
    @CsvSource({
        "'', REQUIRED",
        "licuadora, INVALID_CHARACTERS",
        "750123456789012345678, TOO_LONG",
        "750123456, INVALID_LENGTH"
    })
    void rejectsInvalidEans(String ean, String expectedCode) throws Exception {
        expectProblem(search(ean), expectedCode);
    }

    @Test
    void allowsTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options(PRODUCTS)
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsAnOriginThatIsNotConfigured() throws Exception {
        mockMvc.perform(options(PRODUCTS)
                        .header("Origin", "http://otro.ejemplo")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private ResultActions search(String ean) throws Exception {
        return mockMvc.perform(get(PRODUCTS).param("ean", ean));
    }

    private static void expectEmpty(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private static void expectProblem(ResultActions result, String expectedCode) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist()) // "about:blank" is omitted (RFC 9457 §3.1.1)
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("EAN no válido"))
                .andExpect(jsonPath("$.detail").value("La solicitud tiene datos no válidos."))
                .andExpect(jsonPath("$.instance").value(PRODUCTS))
                .andExpect(jsonPath("$.errors[*].field", everyItem(is("ean"))))
                .andExpect(jsonPath("$.errors[*].code", hasItem(expectedCode)))
                .andExpect(jsonPath("$.errors[*].message").isNotEmpty());
    }
}
