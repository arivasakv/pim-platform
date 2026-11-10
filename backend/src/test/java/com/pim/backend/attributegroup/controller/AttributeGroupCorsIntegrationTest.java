package com.pim.backend.attributegroup.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The groups page renames and deletes groups and removes members, so the configured frontend origin must be allowed
 * to use {@code PUT} and {@code DELETE} with the development permission header (feature 005, plan decision D-9).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeGroupCorsIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private final MockMvc mockMvc;

    AttributeGroupCorsIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void allowsThePreflightFromTheFrontend(String method) throws Exception {
        mockMvc.perform(options("/api/v1/attribute-groups/x")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "content-type,x-pim-permissions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)));
    }
}
