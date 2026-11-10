package com.pim.backend.attribute.controller;

import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.error;
import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.typed;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.attribute.repository.ListOfValuesCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * List-of-values attributes once lists exist (FR-012): the empty catalog is replaced by a mock that holds one list,
 * {@code LOV-COLOR}, so the checks after "lists exist" can be reached over HTTP (constitution VI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttributeListOfValuesIntegrationTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private ListOfValuesCatalog catalog;

    AttributeListOfValuesIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void holdOneList() {
        given(catalog.hasAny()).willReturn(true);
        given(catalog.exists("LOV-COLOR")).willReturn(true);
    }

    @Test
    void createsAnAttributeLinkedToAnExistingList() throws Exception {
        create("{\"selectionMode\":\"MULTIPLE\",\"listOfValuesId\":\"LOV-COLOR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listOfValuesProperties.selectionMode").value("MULTIPLE"))
                .andExpect(jsonPath("$.listOfValuesProperties.listOfValuesId").value("LOV-COLOR"));
    }

    @Test
    void refusesAnUnknownList() throws Exception {
        create("{\"selectionMode\":\"SINGLE\",\"listOfValuesId\":\"LOV-OTRA\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(error("listOfValuesProperties.listOfValuesId", "LIST_OF_VALUES_NOT_FOUND"));
    }

    @Test
    void requiresTheListAndTheSelectionMode() throws Exception {
        create("{\"selectionMode\":\"SINGLE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("listOfValuesProperties.listOfValuesId", "REQUIRED"));
        create("{\"listOfValuesId\":\"LOV-COLOR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("listOfValuesProperties.selectionMode", "REQUIRED"));
    }

    private ResultActions create(String properties) throws Exception {
        return mockMvc.perform(post("/api/v1/attributes")
                .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(typed("Color", "LIST_OF_VALUES", "listOfValuesProperties", properties)));
    }
}
