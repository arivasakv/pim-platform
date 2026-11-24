package com.pim.backend.attribute.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Unlinking a list of values from an attribute over HTTP (feature 014 contract list-unlink.openapi.yaml; FR-001–FR-012).
 * Data in MongoDB, emptied per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeListUnlinkIntegrationTest {

    private static final String ATTRIBUTES = "/api/v1/attributes";
    private static final String NODES = "/api/v1/taxonomy-nodes";
    private static final String COLOR = "ATR000001";
    private static final String TALLA = "ATR000002";
    private static final String ACTIVO = "ATR000003";
    private static final String UNLINK = ATTRIBUTES + "/" + COLOR + "/list-unlink";
    private static final String TO_TEXT =
            "{\"dataType\":\"TEXT\",\"textProperties\":{\"maxLength\":40}," + "\"reviewToken\":\"%s\"}";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;
    private String ropa;
    private String camisas;
    private String group;

    AttributeListUnlinkIntegrationTest(
            @Autowired MockMvc mockMvc,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.mockMvc = mockMvc;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    private ResultActions change(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                .content(body));
    }

    private String created(MockHttpServletRequestBuilder request, String body, String idPath) throws Exception {
        String response = change(request, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, idPath);
    }

    /** Colores (LOV000001) and Tallas (LOV000002); Color and Talla use them; Activo is Boolean. Color is in group
     * «Ropa», assigned to «Ropa», and filtered on «Ropa» and «Camisas»; Talla is filtered on «Ropa». */
    @BeforeEach
    void buildTheExample() throws Exception {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
        for (String list : new String[] {"Colores", "Tallas"}) {
            change(post("/api/v1/lists-of-values"), "{\"name\":\"" + list + "\"}")
                    .andExpect(status().isCreated());
        }
        for (String list : new String[] {"LOV000001", "LOV000002"}) {
            change(post("/api/v1/lists-of-values/" + list + "/values"), "{\"id\":\"1\",\"label\":\"Uno\"}")
                    .andExpect(status().isCreated());
        }
        change(post(ATTRIBUTES), """
                        {"displayName":"Color","dataType":"LIST_OF_VALUES",
                         "listOfValuesProperties":{"selectionMode":"SINGLE","listOfValuesId":"LOV000001"}}""").andExpect(status().isCreated());
        change(post(ATTRIBUTES), """
                        {"displayName":"Talla","dataType":"LIST_OF_VALUES",
                         "listOfValuesProperties":{"selectionMode":"SINGLE","listOfValuesId":"LOV000002"}}""").andExpect(status().isCreated());
        change(post(ATTRIBUTES), "{\"displayName\":\"Activo\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isCreated());
        group = created(post("/api/v1/attribute-groups"), "{\"name\":\"Ropa\"}", "$.id");
        change(post("/api/v1/attribute-groups/" + group + "/attributes"), "{\"attributeIds\":[\"" + COLOR + "\"]}")
                .andExpect(status().isOk());
        ropa = created(post(NODES), "{\"name\":\"Ropa\"}", "$.systemId");
        camisas = created(post(NODES), "{\"name\":\"Camisas\",\"parentId\":\"" + ropa + "\"}", "$.systemId");
        change(post(NODES + "/" + ropa + "/attributes"), "{\"attributeIds\":[\"" + COLOR + "\",\"" + TALLA + "\"]}")
                .andExpect(status().isOk());
        for (String[] filter : new String[][] {{ropa, COLOR}, {camisas, COLOR}, {ropa, TALLA}}) {
            change(put(NODES + "/" + filter[0] + "/value-filters/" + filter[1]), "{\"valueIds\":[\"1\"]}")
                    .andExpect(status().isOk());
        }
    }

    private String token() throws Exception {
        String review = mockMvc.perform(get(UNLINK))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(review, "$.reviewToken");
    }

    private static ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }

    @Test
    void reviewsWhatTheUnlinkAffects() throws Exception {
        mockMvc.perform(get(UNLINK.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributeId").value(COLOR))
                .andExpect(jsonPath("$.attributeName").value("Color"))
                .andExpect(jsonPath("$.listOfValuesId").value("LOV000001"))
                .andExpect(jsonPath("$.listOfValuesName").value("Colores"))
                .andExpect(jsonPath("$.filteredNodes[0].name").value("Camisas"))
                .andExpect(jsonPath("$.filteredNodes[1].name").value("Ropa"))
                .andExpect(jsonPath("$.filteredNodes.length()").value(2))
                .andExpect(jsonPath("$.reviewToken").isString());
    }

    @Test
    void unlinksKeepingGroupsAndAssignmentsAndDeletingOnlyItsFilters() throws Exception {
        change(post(UNLINK), TO_TEXT.formatted(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value(COLOR))
                .andExpect(jsonPath("$.displayName").value("Color"))
                .andExpect(jsonPath("$.dataType").value("TEXT"))
                .andExpect(jsonPath("$.textProperties.maxLength").value(40))
                .andExpect(jsonPath("$.listOfValuesProperties").doesNotExist())
                .andExpect(jsonPath("$.groups[0].name").value("Ropa"))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));

        mockMvc.perform(get("/api/v1/lists-of-values/LOV000001"))
                .andExpect(jsonPath("$.attributes").isEmpty());
        mockMvc.perform(get(NODES + "/" + ropa))
                .andExpect(jsonPath("$.effectiveAttributes[?(@.systemId == '" + COLOR + "')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.listAttributes.length()").value(1))
                .andExpect(jsonPath("$.listAttributes[0].systemId").value(TALLA))
                .andExpect(jsonPath("$.listAttributes[0].hasOwnFilter").value(true));
        mockMvc.perform(get(NODES + "/" + camisas + "/value-filters/" + COLOR)).andExpect(status().is4xxClientError());
    }

    @Test
    void refusesInvalidRequestsWithoutChangingAnything() throws Exception {
        String token = token();

        change(post(UNLINK), "{\"dataType\":\"TEXT\",\"textProperties\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(error("reviewToken", "REVIEW_TOKEN_REQUIRED"));
        change(post(UNLINK), """
                        {"dataType":"NUMERIC","numericProperties":{"numberFormat":"INTEGER","precision":2},
                         "reviewToken":"%s"}""".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(error("numericProperties.precision", "INVALID_PRECISION"));
        change(post(UNLINK), """
                        {"dataType":"LIST_OF_VALUES",
                         "listOfValuesProperties":{"selectionMode":"SINGLE","listOfValuesId":"LOV000002"},
                         "reviewToken":"%s"}""".formatted(token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("dataType", "UNLINK_TO_LIST_NOT_ALLOWED"));
        change(post(UNLINK), TO_TEXT.formatted("otro"))
                .andExpect(status().isConflict())
                .andExpect(error("reviewToken", "UNLINK_OUTDATED"));
        mockMvc.perform(post(UNLINK).contentType(MediaType.APPLICATION_JSON).content(TO_TEXT.formatted(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ATTRIBUTES + "/" + ACTIVO + "/list-unlink"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("dataType", "ATTRIBUTE_NOT_LIST_OF_VALUES"));
        mockMvc.perform(get(ATTRIBUTES + "/ATR999999/list-unlink")).andExpect(status().isNotFound());

        mockMvc.perform(get(ATTRIBUTES + "/" + COLOR))
                .andExpect(jsonPath("$.dataType").value("LIST_OF_VALUES"));
        mockMvc.perform(get(UNLINK))
                .andExpect(jsonPath("$.filteredNodes.length()").value(2));
    }

    @Test
    void refusesWhenAFilterWasAddedAfterTheReview() throws Exception {
        String token = token();
        String hombre = created(post(NODES), "{\"name\":\"Hombre\",\"parentId\":\"" + ropa + "\"}", "$.systemId");
        change(put(NODES + "/" + hombre + "/value-filters/" + COLOR), "{\"valueIds\":[\"1\"]}")
                .andExpect(status().isOk());

        change(post(UNLINK), TO_TEXT.formatted(token)).andExpect(status().isConflict());
    }
}
