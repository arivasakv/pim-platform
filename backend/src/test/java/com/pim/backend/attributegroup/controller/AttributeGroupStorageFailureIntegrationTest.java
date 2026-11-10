package com.pim.backend.attributegroup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.attributegroup.model.GroupName;
import com.pim.backend.attributegroup.repository.AttributeGroupRepository;
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
 * When the group store cannot be reached, requests answer 503 with the project's Spanish problem body and nothing is
 * half-saved (spec A-006, edge case "the system cannot store the change"; the group error handler must not hide the
 * shared {@code common.DataAccessExceptionHandler}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeGroupStorageFailureIntegrationTest {

    @MockitoBean
    private AttributeGroupRepository groups;

    private final MockMvc mockMvc;

    AttributeGroupStorageFailureIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void creatingAnswers503AndSavesNothing() throws Exception {
        given(groups.existsWithName(any(GroupName.class), isNull()))
                .willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(post("/api/v1/attribute-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .content("{\"name\":\"Logísticos\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(ApiProblems.SERVICE_UNAVAILABLE_TITLE))
                .andExpect(jsonPath("$.detail").value(ApiProblems.SERVICE_UNAVAILABLE_DETAIL));

        then(groups).should(never()).insert(any());
        then(groups).should(never()).update(any());
        then(groups).should(never()).delete(anyString());
    }

    @Test
    void savingAnswers503AndWritesNothingElse() throws Exception {
        given(groups.existsWithName(any(GroupName.class), isNull())).willReturn(false);
        willThrow(new DataAccessResourceFailureException("MongoDB unreachable"))
                .given(groups)
                .insert(any());

        mockMvc.perform(post("/api/v1/attribute-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .content("{\"name\":\"Logísticos\"}"))
                .andExpect(status().isServiceUnavailable());

        then(groups).should(atMost(1)).insert(any());
        then(groups).should(never()).update(any());
        then(groups).should(never()).delete(anyString());
    }

    @Test
    void listingAnswers503() throws Exception {
        given(groups.findAll()).willThrow(new DataAccessResourceFailureException("MongoDB unreachable"));

        mockMvc.perform(get("/api/v1/attribute-groups"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(ApiProblems.SERVICE_UNAVAILABLE_TITLE));
    }
}
