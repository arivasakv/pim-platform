package com.pim.backend.attribute.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.attribute.repository.AttributeRepository;
import com.pim.backend.common.ApiProblems;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * When the attribute store cannot be reached, edits and deletions answer 503 with the project's Spanish problem body
 * and change nothing (feature 007 edge case "the system cannot store the change"; the attribute error handler must
 * not hide the shared {@code common.DataAccessExceptionHandler}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeChangeStorageFailureIntegrationTest {

    @MockitoBean
    private AttributeRepository attributes;

    private final MockMvc mockMvc;

    AttributeChangeStorageFailureIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void editingAnswers503AndSavesNothing() throws Exception {
        given(attributes.findBySystemId(anyString()))
                .willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(put("/api/v1/attributes/ATR000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .content("{\"displayName\":\"Activo\",\"dataType\":\"BOOLEAN\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(ApiProblems.SERVICE_UNAVAILABLE_TITLE))
                .andExpect(jsonPath("$.detail").value(ApiProblems.SERVICE_UNAVAILABLE_DETAIL));

        then(attributes).should(never()).update(any());
    }

    @Test
    void deletingAnswers503AndDeletesNothing() throws Exception {
        given(attributes.findBySystemId(anyString()))
                .willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(delete("/api/v1/attributes/ATR000001").header("X-Pim-Permissions", "ATTRIBUTE_CREATE"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(ApiProblems.SERVICE_UNAVAILABLE_TITLE));

        then(attributes).should(never()).delete(anyString());
    }
}
