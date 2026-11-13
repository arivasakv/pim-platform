package com.pim.backend.taxonomy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.common.ApiProblems;
import com.pim.backend.taxonomy.repository.TaxonomyNodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * When the taxonomy store cannot be reached, requests answer 503 with the project's Spanish problem body and nothing
 * is half-saved (spec edge case "the backend cannot be reached"; the taxonomy error handler must not hide {@code
 * common.DataAccessExceptionHandler}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaxonomyStorageFailureIntegrationTest {

    @MockitoBean
    private TaxonomyNodeRepository nodes;

    private final MockMvc mockMvc;

    TaxonomyStorageFailureIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void readingAnswers503() throws Exception {
        given(nodes.findAll()).willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(get("/api/v1/taxonomy-nodes"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(ApiProblems.SERVICE_UNAVAILABLE_TITLE))
                .andExpect(jsonPath("$.detail").value(ApiProblems.SERVICE_UNAVAILABLE_DETAIL));
    }

    @Test
    void creatingAnswers503AndSavesNothing() throws Exception {
        given(nodes.existsWithName(any(), any(), any()))
                .willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(post("/api/v1/taxonomy-nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .content("{\"name\":\"Ropa\"}"))
                .andExpect(status().isServiceUnavailable());

        then(nodes).should(never()).insert(any());
        then(nodes).should(never()).update(any());
        then(nodes).should(never()).delete(anyString());
    }
}
