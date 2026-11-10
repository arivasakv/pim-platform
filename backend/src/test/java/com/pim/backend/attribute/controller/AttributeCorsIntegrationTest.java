package com.pim.backend.attribute.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The browser form posts attributes, so the configured frontend origin must be allowed to use {@code POST} with
 * the development permission headers (research R-17).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeCorsIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private final MockMvc mockMvc;

    AttributeCorsIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/attributes", "/api/v1/system-ids"})
    void allowsThePostPreflightFromTheFrontend(String path) throws Exception {
        mockMvc.perform(options(path)
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header(
                                "Access-Control-Request-Headers",
                                "content-type,x-pim-permissions,x-pim-manual-id-permissions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void stillAllowsGet() throws Exception {
        mockMvc.perform(options("/api/v1/attribute-types")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }
}
