package com.pim.backend.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.product.model.Ean;
import com.pim.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/products?ean=} when the catalog store cannot be reached: 503 Problem Details, no
 * products (contract 1.1.0; docs/decisions/0002-mongodb-product-catalog.md). The repository is replaced
 * by a mock that fails the way {@code MongoProductRepository} does when MongoDB is down.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerUnavailableIntegrationTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;

    ProductControllerUnavailableIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void answers503WhenTheCatalogStoreIsUnreachable() throws Exception {
        given(productRepository.findByEan(any(Ean.class)))
                .willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(get("/api/v1/products").param("ean", "7501234567890"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.detail")
                        .value("No se pudieron consultar los datos en este momento. Inténtalo de nuevo más tarde."))
                .andExpect(jsonPath("$.instance").value("/api/v1/products"))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void stillRejectsAnInvalidEanBeforeReachingTheStore() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("ean", "750123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_LENGTH"));
    }
}
