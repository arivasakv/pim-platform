package com.pim.backend.taxonomy.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

/**
 * API integration tests for list-value filters, {@code /api/v1/taxonomy-nodes/{nodeId}/value-filters/{attributeId}}
 * and the node's {@code listAttributes} (feature 012; taxonomy contract v1.2.0). Data in MongoDB, emptied per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValueFilterControllerIntegrationTest {

    private static final String NODES = "/api/v1/taxonomy-nodes";
    private static final String PERMISSIONS = "X-Pim-Permissions";
    private static final String CAN_CHANGE = "ATTRIBUTE_CREATE";
    private static final String COLOR = "ATR000001";
    private static final String LARGO = "ATR000002";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaultConfigurations;
    private String ropa;
    private String hombre;
    private String camisas;
    private String formales;
    private String mujer;
    private String calzado;

    ValueFilterControllerIntegrationTest(
            @Autowired MockMvc mockMvc,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaultConfigurations) {
        this.mockMvc = mockMvc;
        this.mongoTemplate = mongoTemplate;
        this.defaultConfigurations = defaultConfigurations;
    }

    @BeforeEach
    void buildTheExample() throws Exception {
        AttributeAdministrationTestData.reset(mongoTemplate, defaultConfigurations);
        change(post("/api/v1/lists-of-values"), "{\"name\":\"Colores\"}").andExpect(status().isCreated());
        String[][] colores = {
            {"0001", "Rojo"},
            {"0002", "Azul"},
            {"0003", "Verde"},
            {"0004", "Negro"},
            {"0005", "Blanco"},
            {"0006", "Amarillo"},
            {"0007", "Rosa"}
        };
        for (String[] value : colores) {
            change(
                            post("/api/v1/lists-of-values/LOV000001/values"),
                            "{\"id\":\"%s\",\"label\":\"%s\"}".formatted(value[0], value[1]))
                    .andExpect(status().isCreated());
        }
        change(post("/api/v1/attributes"), """
                        {"displayName":"Color","dataType":"LIST_OF_VALUES",
                         "listOfValuesProperties":{"selectionMode":"SINGLE","listOfValuesId":"LOV000001"}}""").andExpect(status().isCreated());
        change(post("/api/v1/attributes"), "{\"displayName\":\"Largo de manga\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isCreated());
        ropa = createNode("Ropa", null);
        hombre = createNode("Ropa de hombre", ropa);
        camisas = createNode("Camisas", hombre);
        formales = createNode("Camisas formales", camisas);
        mujer = createNode("Ropa de mujer", ropa);
        calzado = createNode("Calzado", null);
        assign(ropa, COLOR);
        assign(camisas, LARGO);
        assign(calzado, COLOR);
    }

    @Test
    void savesAFilterAndShowsItInTheNodeAndBelow() throws Exception {
        mockMvc.perform(get(filterPath(hombre, COLOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValues").value(7))
                .andExpect(jsonPath("$.availableValues.length()").value(7))
                .andExpect(jsonPath("$.includedValueIds").value(nullValue()))
                .andExpect(jsonPath("$.attribute.listName").value("Colores"))
                .andExpect(jsonPath("$.attribute.selectionMode").value("SINGLE"));

        save(hombre, COLOR, "0002", "0004", "0005", "0001")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.node.name").value("Ropa de hombre"))
                .andExpect(jsonPath("$.includedValueIds", contains("0001", "0002", "0004", "0005")))
                .andExpect(jsonPath("$.allowedValues[*].label", contains("Rojo", "Azul", "Negro", "Blanco")))
                .andExpect(jsonPath("$.attribute.allowedCount").value(4))
                .andExpect(jsonPath("$.attribute.hasOwnFilter").value(true))
                .andExpect(jsonPath("$.filters[0].node.name").value("Ropa de hombre"))
                .andExpect(jsonPath("$.filters[0].includedCount").value(4));

        mockMvc.perform(get(NODES + "/" + formales))
                .andExpect(jsonPath("$.listAttributes[0].systemId").value(COLOR))
                .andExpect(jsonPath("$.listAttributes[0].totalValues").value(7))
                .andExpect(jsonPath("$.listAttributes[0].allowedCount").value(4))
                .andExpect(jsonPath("$.listAttributes[0].hasOwnFilter").value(false))
                .andExpect(jsonPath("$.listAttributes[0].filters[0].node.name").value("Ropa de hombre"))
                .andExpect(jsonPath("$.listAttributes.length()").value(1));
        mockMvc.perform(get(NODES + "/" + mujer))
                .andExpect(jsonPath("$.listAttributes[0].allowedCount").value(7));
        mockMvc.perform(get(NODES + "/" + ropa))
                .andExpect(jsonPath("$.listAttributes[0].filters").isEmpty());
    }

    @Test
    void refusesInvalidFiltersWithTheContractCodes() throws Exception {
        save(hombre, COLOR).andExpect(status().isBadRequest()).andExpect(error("valueIds", "INVALID_FILTER_VALUES"));
        change(put(filterPath(hombre, COLOR)), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(error("valueIds", "REQUIRED"));
        save("CLS999999", COLOR, "0001").andExpect(status().isNotFound()).andExpect(error("nodeId", "NODE_NOT_FOUND"));
        save(hombre, "ATR999999", "0001")
                .andExpect(status().isNotFound())
                .andExpect(error("attributeId", "ATTRIBUTE_NOT_FOUND"));
        save(hombre, LARGO, "0001")
                .andExpect(status().isNotFound())
                .andExpect(error("attributeId", "ATTRIBUTE_NOT_ON_NODE"));
        save(camisas, LARGO, "0001")
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("attributeId", "ATTRIBUTE_NOT_LIST"))
                .andExpect(jsonPath("$.title").value("Atributo sin lista de valores"));
        save(hombre, COLOR, "9999")
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("valueIds", "VALUE_NOT_IN_LIST"))
                .andExpect(jsonPath("$.detail").value("La lista «Colores» no tiene un valor con el ID 9999."));
        mockMvc.perform(put(filterPath(hombre, COLOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valueIds\":[\"0001\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "TAXONOMY_CHANGE_NOT_PERMITTED"));
        mockMvc.perform(get(NODES + "/" + hombre))
                .andExpect(jsonPath("$.listAttributes[0].filters").isEmpty());
    }

    @Test
    void restrictsBelowAndReportsValuesExcludedAbove() throws Exception {
        save(hombre, COLOR, "0001", "0002", "0004", "0005").andExpect(status().isOk());

        save(camisas, COLOR, "0002", "0003")
                .andExpect(status().isConflict())
                .andExpect(error("valueIds", "VALUE_EXCLUDED_ABOVE"))
                .andExpect(jsonPath("$.title").value("Valor excluido arriba"))
                .andExpect(jsonPath("$.detail")
                        .value("El valor «Verde» (0003) no está permitido por el filtro de «Ropa de hombre». Solo"
                                + " puedes incluir valores permitidos arriba."));
        save(camisas, COLOR, "0002", "0005").andExpect(status().isOk());
        save(hombre, COLOR, "0002", "0004").andExpect(status().isOk());

        mockMvc.perform(get(filterPath(camisas, COLOR)))
                .andExpect(jsonPath("$.allowedValues[*].label", contains("Azul")))
                .andExpect(jsonPath("$.includedValueIds", contains("0002", "0005")))
                .andExpect(jsonPath("$.excludedAboveValues[0].label").value("Blanco"))
                .andExpect(jsonPath("$.excludedAboveValues[0].excludedBy.name").value("Ropa de hombre"))
                .andExpect(jsonPath("$.filters[*].node.name", contains("Ropa de hombre", "Camisas")));
    }

    @Test
    void removesFiltersAndFollowsMoves() throws Exception {
        save(hombre, COLOR, "0001", "0002", "0004", "0005").andExpect(status().isOk());
        save(camisas, COLOR, "0002", "0005").andExpect(status().isOk());

        mockMvc.perform(delete(filterPath(camisas, COLOR)).header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(filterPath(camisas, COLOR)).header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isNotFound())
                .andExpect(error("attributeId", "FILTER_NOT_FOUND"));
        mockMvc.perform(delete(filterPath(hombre, COLOR))).andExpect(status().isForbidden());
        mockMvc.perform(get(NODES + "/" + formales))
                .andExpect(jsonPath("$.listAttributes[0].allowedCount").value(4));

        change(post(NODES + "/" + camisas + "/move"), "{\"parentId\":\"" + mujer + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node.listAttributes[0].allowedCount").value(7));
        mockMvc.perform(get(NODES + "/" + formales))
                .andExpect(jsonPath("$.listAttributes[0].allowedCount").value(7))
                .andExpect(jsonPath("$.listAttributes[0].filters").isEmpty())
                .andExpect(jsonPath("$.updatedAt", nullValue()));
    }

    @Test
    void deletingANodeDeletesItsFilters() throws Exception {
        save(formales, COLOR, "0001").andExpect(status().isOk());

        mockMvc.perform(delete(NODES + "/" + formales).header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(mongoTemplate.count(
                        new org.springframework.data.mongodb.core.query.Query(), "taxonomy_value_filters"))
                .isZero();
        mockMvc.perform(get(NODES + "/" + calzado)).andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    // --- helpers ---

    private static String filterPath(String nodeId, String attributeId) {
        return NODES + "/" + nodeId + "/value-filters/" + attributeId;
    }

    private ResultActions change(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content(body));
    }

    private ResultActions save(String nodeId, String attributeId, String... valueIds) throws Exception {
        String ids = Stream.of(valueIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return change(put(filterPath(nodeId, attributeId)), "{\"valueIds\":[" + ids + "]}");
    }

    private String createNode(String name, String parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        String body = change(post(NODES), "{\"name\":\"" + name + "\"" + parent + "}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.systemId");
    }

    private void assign(String nodeId, String attributeId) throws Exception {
        change(post(NODES + "/" + nodeId + "/attributes"), "{\"attributeIds\":[\"" + attributeId + "\"]}")
                .andExpect(status().isOk());
    }

    private static ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }
}
