package com.pim.backend.lov.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.common.ApiProblems;
import com.pim.backend.lov.repository.ListOfValuesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** An unreachable store answers 503 with the shared problem body, and nothing is stored (edge case "storage"). */
@SpringBootTest
@AutoConfigureMockMvc
class ListOfValuesStorageFailureIntegrationTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private ListOfValuesRepository lists;

    ListOfValuesStorageFailureIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void answers503WhenTheStoreCannotBeReached() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("down");
        given(lists.findAll()).willThrow(failure);
        given(lists.existsWithName(any(), any())).willThrow(failure);

        mockMvc.perform(get("/api/v1/lists-of-values"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(ApiProblems.SERVICE_UNAVAILABLE_TITLE));
        mockMvc.perform(post("/api/v1/lists-of-values")
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Color\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(ApiProblems.SERVICE_UNAVAILABLE_DETAIL));
        verify(lists, never()).insert(any());
    }
}
