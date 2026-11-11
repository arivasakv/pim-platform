package com.pim.backend.lov.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The lists page renames, deletes and uploads, so the frontend origin must pass the preflight (research R-13). */
@SpringBootTest
@AutoConfigureMockMvc
class ListOfValuesCorsIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private final MockMvc mockMvc;

    ListOfValuesCorsIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @CsvSource({
        "PUT, /api/v1/lists-of-values/x",
        "DELETE, /api/v1/lists-of-values/x/values/y",
        "POST, /api/v1/lists-of-values/x/values/import"
    })
    void allowsThePreflightFromTheFrontend(String method, String path) throws Exception {
        mockMvc.perform(options(path)
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "x-pim-permissions,x-pim-user"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)));
    }
}
