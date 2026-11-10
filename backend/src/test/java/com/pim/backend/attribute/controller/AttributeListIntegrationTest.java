package com.pim.backend.attribute.controller;

import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.ATTRIBUTES;
import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.CAN_CREATE;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * {@code GET /api/v1/attributes}: every attribute, sorted by display name ignoring letter case (feature 005 plan
 * decision D-3; feature 004 contract v1.1.0). The groups page offers these attributes to choose from.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeListIntegrationTest {

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaultConfigurations;

    AttributeListIntegrationTest(
            @Autowired MockMvc mockMvc,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaultConfigurations) {
        this.mockMvc = mockMvc;
        this.mongoTemplate = mongoTemplate;
        this.defaultConfigurations = defaultConfigurations;
    }

    @BeforeEach
    void startFromAFreshInstall() {
        AttributeAdministrationTestData.reset(mongoTemplate, defaultConfigurations);
    }

    @Test
    void listsNoAttributesAtFirst() throws Exception {
        mockMvc.perform(get(ATTRIBUTES))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listsEveryAttributeSortedByDisplayNameIgnoringCase() throws Exception {
        create("{\"displayName\":\"Volumen\",\"dataType\":\"BOOLEAN\"}");
        create(
                "{\"displayName\":\"peso\",\"dataType\":\"NUMERIC\",\"numericProperties\":{\"numberFormat\":\"INTEGER\"}}");
        create("{\"displayName\":\"Color\",\"dataType\":\"TEXT\",\"textProperties\":{\"maxLength\":20}}");

        mockMvc.perform(get(ATTRIBUTES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].displayName").value(contains("Color", "peso", "Volumen")))
                .andExpect(jsonPath("$.items[*].systemId").value(contains("ATR000003", "ATR000002", "ATR000001")))
                .andExpect(jsonPath("$.items[0].dataType").value("TEXT"))
                .andExpect(jsonPath("$.items[0].scope").value("GLOBAL"))
                .andExpect(jsonPath("$.items[0].textProperties.maxLength").value(20))
                .andExpect(jsonPath("$.items[0].numericProperties").doesNotExist())
                .andExpect(jsonPath("$.items[1].numericProperties.numberFormat").value("INTEGER"))
                .andExpect(jsonPath("$.items[1].textProperties").doesNotExist())
                .andExpect(jsonPath("$.items[2].textProperties").doesNotExist())
                .andExpect(jsonPath("$.items[2].createdAt").exists());
    }

    private void create(String body) throws Exception {
        mockMvc.perform(post(ATTRIBUTES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
