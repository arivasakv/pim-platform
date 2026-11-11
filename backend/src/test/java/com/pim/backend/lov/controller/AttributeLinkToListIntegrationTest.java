package com.pim.backend.lov.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Feature 004 links list-of-values attributes to real lists through the catalog adapter (FR-026, SC-006). */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeLinkToListIntegrationTest {

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    AttributeLinkToListIntegrationTest(
            @Autowired MockMvc mockMvc,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.mockMvc = mockMvc;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    @BeforeEach
    void startFromAFreshInstall() {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
    }

    @Test
    void withoutListsFeature004KeepsRefusingListOfValuesAttributes() throws Exception {
        createAttribute("Color", "LOV000001")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("LIST_OF_VALUES_NOT_AVAILABLE"));
    }

    @Test
    void linksToAnExistingListIgnoringCaseAndRefusesAnUnknownOne() throws Exception {
        mockMvc.perform(post("/api/v1/lists-of-values")
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Color\"}"))
                .andExpect(status().isCreated());

        createAttribute("Color", "lov000001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listOfValuesProperties.listOfValuesId").value("lov000001"));
        createAttribute("Talla", "LOV999999")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("LIST_OF_VALUES_NOT_FOUND"));
    }

    private ResultActions createAttribute(String name, String listId) throws Exception {
        return mockMvc.perform(post("/api/v1/attributes")
                .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"%s","dataType":"LIST_OF_VALUES",
                         "listOfValuesProperties":{"selectionMode":"MULTIPLE","listOfValuesId":"%s"}}""".formatted(name, listId)));
    }
}
