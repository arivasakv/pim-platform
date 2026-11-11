package com.pim.backend.lov.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * API integration tests for {@code /api/v1/lists-of-values} and its values, following
 * specs/006-lov-management/contracts/lists-of-values.openapi.yaml. Data is stored in MongoDB; each test starts from
 * emptied collections.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ListOfValuesControllerIntegrationTest {

    private static final String LISTS = "/api/v1/lists-of-values";
    private static final String PERMISSIONS = "X-Pim-Permissions";
    private static final String CAN_CHANGE = "ATTRIBUTE_CREATE";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    ListOfValuesControllerIntegrationTest(
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

    // --- User Story 1: create and add values ---

    @Test
    void createsAList() throws Exception {
        changing(post(LISTS), "{\"name\":\"Color\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", LISTS + "/LOV000001"))
                .andExpect(jsonPath("$.systemId").value("LOV000001"))
                .andExpect(jsonPath("$.name").value("Color"))
                .andExpect(jsonPath("$.equivalencesEnabled").value(false))
                .andExpect(jsonPath("$.valueCount").value(0))
                .andExpect(jsonPath("$.values").isEmpty())
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void validatesTheListName() throws Exception {
        createList("Color");

        expectError(changing(post(LISTS), "{\"name\":\" color \"}"), 409, "name", "LIST_NAME_IN_USE")
                .andExpect(jsonPath("$.title").value("Nombre en uso"));
        expectError(changing(post(LISTS), "{\"name\":\"  \"}"), 400, "name", "INVALID_LIST_NAME");
        expectError(changing(post(LISTS), "{\"name\":\"" + "n".repeat(81) + "\"}"), 400, "name", "INVALID_LIST_NAME");
        expectError(changing(post(LISTS), "{}"), 400, "name", "REQUIRED");
    }

    @Test
    void changesNeedThePermission() throws Exception {
        expectError(
                        mockMvc.perform(post(LISTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Color\"}")),
                        403,
                        "permissions",
                        "ATTRIBUTE_CREATE_NOT_PERMITTED")
                .andExpect(jsonPath("$.title").value("Sin permiso para administrar listas de valores"));
        createList("Color");
        mockMvc.perform(post(LISTS + "/LOV000001/values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Azul\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(LISTS + "/LOV000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"equivalencesEnabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(LISTS + "/LOV000001")).andExpect(status().isForbidden());
    }

    @Test
    void aManualSystemIdNeedsFeature003sPermission() throws Exception {
        expectError(
                changing(post(LISTS), "{\"name\":\"Color\",\"systemId\":\"LOV-COLOR\"}"),
                403,
                "systemId",
                "MANUAL_ID_NOT_PERMITTED");

        mockMvc.perform(post(LISTS)
                        .header(PERMISSIONS, CAN_CHANGE)
                        .header("X-Pim-Manual-Id-Permissions", "LIST_OF_VALUES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Color\",\"systemId\":\"LOV-COLOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemId").value("LOV-COLOR"));
    }

    @Test
    void addsValues() throws Exception {
        createList("Color");

        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"0001\",\"label\":\"Azul\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", LISTS + "/LOV000001/values/0001"))
                .andExpect(jsonPath("$.id").value("0001"))
                .andExpect(jsonPath("$.label").value("Azul"))
                .andExpect(jsonPath("$.equivalence").isEmpty());
        changing(post(LISTS + "/lov000001/values"), "{\"label\":\"Rojo\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("0002"));
    }

    @Test
    void validatesValues() throws Exception {
        createList("Color");
        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"0001\",\"label\":\"Azul\"}");

        expectError(
                        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"0001\",\"label\":\"Otro\"}"),
                        409,
                        "id",
                        "VALUE_ID_IN_USE")
                .andExpect(jsonPath("$.detail").value("Ya existe un valor con el ID \"0001\" en esta lista."));
        expectError(
                changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"a b\",\"label\":\"X\"}"),
                400,
                "id",
                "INVALID_VALUE_ID");
        expectError(changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"9\"}"), 400, "label", "REQUIRED");
        expectError(
                changing(post(LISTS + "/LOV000001/values"), "{\"label\":\"" + "x".repeat(201) + "\"}"),
                400,
                "label",
                "INVALID_LABEL");
        expectError(changing(post(LISTS + "/LOV999999/values"), "{\"label\":\"X\"}"), 404, "listId", "LIST_NOT_FOUND")
                .andExpect(jsonPath("$.title").value("Lista no encontrada"));
    }

    @Test
    void theSameValueIdMayBeUsedInAnotherList() throws Exception {
        createList("Color");
        createList("Talla");
        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"0001\",\"label\":\"Azul\"}");

        changing(post(LISTS + "/LOV000002/values"), "{\"id\":\"0001\",\"label\":\"Chica\"}")
                .andExpect(status().isCreated());
    }

    // --- User Story 2: consult ---

    @Test
    void listsAndShowsListsWithoutPermission() throws Exception {
        mockMvc.perform(get(LISTS))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
        createList("Talla");
        createList("Color");
        changing(post(LISTS + "/LOV000002/values"), "{\"id\":\"z\",\"label\":\"Zeta\"}");
        changing(post(LISTS + "/LOV000002/values"), "{\"id\":\"a\",\"label\":\"A\"}");
        createLinkedAttribute("Tono", "MULTIPLE", "LOV000002");
        createLinkedAttribute("Color base", "SINGLE", "lov000002");

        mockMvc.perform(get(LISTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].name", contains("Color", "Talla")))
                .andExpect(jsonPath("$.items[0].systemId").value("LOV000002"))
                .andExpect(jsonPath("$.items[0].valueCount").value(2))
                .andExpect(jsonPath("$.items[0].attributeCount").value(2))
                .andExpect(jsonPath("$.items[1].attributeCount").value(0));
        mockMvc.perform(get(LISTS + "/lov000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values[*].id", contains("z", "a")))
                .andExpect(jsonPath("$.attributes[*].displayName", contains("Color base", "Tono")))
                .andExpect(jsonPath("$.attributes[*].selectionMode", contains("SINGLE", "MULTIPLE")))
                .andExpect(jsonPath("$.attributes[0].systemId").isNotEmpty());
        expectError(mockMvc.perform(get(LISTS + "/LOV999999")), 404, "listId", "LIST_NOT_FOUND");
    }

    // --- User Story 3: edit and delete ---

    @Test
    void editsAndDeletesValues() throws Exception {
        createList("Color");
        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"0001\",\"label\":\"Azul\"}");
        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"0002\",\"label\":\"Rojo\"}");

        changing(put(LISTS + "/LOV000001/values/0001"), "{\"label\":\"AZUL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("0001"))
                .andExpect(jsonPath("$.label").value("AZUL"));
        expectError(
                        changing(put(LISTS + "/LOV000001/values/0009"), "{\"label\":\"X\"}"),
                        404,
                        "valueId",
                        "VALUE_NOT_FOUND")
                .andExpect(jsonPath("$.title").value("Valor no encontrado"));

        mockMvc.perform(delete(LISTS + "/LOV000001/values/0002")
                        .header(PERMISSIONS, CAN_CHANGE)
                        .header("X-Pim-User", " ana "))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(LISTS + "/LOV000001/values/0002").header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isNotFound());

        List<Document> records = mongoTemplate.find(new Query(), Document.class, "lov_changes");
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.get("operation")).isEqualTo("VALUE_DELETED");
            assertThat(record.get("user")).isEqualTo("ana");
            assertThat(record.getList("valueIds", String.class)).containsExactly("0002");
        });
    }

    @Test
    void renamesAndDeletesLists() throws Exception {
        createList("Color");
        createList("Talla");
        createList("Temporal");
        createLinkedAttribute("Tono", "SINGLE", "LOV000001");
        createLinkedAttribute("Color base", "MULTIPLE", "LOV000001");

        changing(put(LISTS + "/LOV000001"), "{\"name\":\"Colores\",\"equivalencesEnabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Colores"))
                .andExpect(jsonPath("$.attributes.length()").value(2));
        expectError(
                changing(put(LISTS + "/LOV000001"), "{\"name\":\"talla\",\"equivalencesEnabled\":false}"),
                409,
                "name",
                "LIST_NAME_IN_USE");

        expectError(
                        mockMvc.perform(delete(LISTS + "/LOV000001").header(PERMISSIONS, CAN_CHANGE)),
                        409,
                        "listId",
                        "LIST_IN_USE")
                .andExpect(jsonPath("$.title").value("Lista en uso"))
                .andExpect(jsonPath("$.detail")
                        .value("La lista \"Colores\" está vinculada a 2 atributos y no se puede eliminar."));

        mockMvc.perform(delete(LISTS + "/lov000003").header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(LISTS + "/LOV000003")).andExpect(status().isNotFound());
        assertThat(mongoTemplate.find(new Query(), Document.class, "lov_changes"))
                .extracting(record -> record.get("operation"))
                .containsExactly("LIST_DELETED");
    }

    // --- User Story 5: equivalences ---

    @Test
    void turnsEquivalencesOnAndOffKeepingThem() throws Exception {
        createList("Color");
        changing(put(LISTS + "/LOV000001"), "{\"name\":\"Color\",\"equivalencesEnabled\":true}")
                .andExpect(jsonPath("$.equivalencesEnabled").value(true));
        changing(
                post(LISTS + "/LOV000001/values"),
                "{\"id\":\"1\",\"label\":\"Negro intenso\",\"equivalence\":\"NEGRO\"}");
        changing(post(LISTS + "/LOV000001/values"), "{\"id\":\"2\",\"label\":\"Negro mate\",\"equivalence\":\"NEGRO\"}")
                .andExpect(status().isCreated());
        changing(put(LISTS + "/LOV000001/values/1"), "{\"label\":\"AZUL\",\"equivalence\":\"BLUE\"}")
                .andExpect(jsonPath("$.equivalence").value("BLUE"));
        changing(put(LISTS + "/LOV000001/values/2"), "{\"label\":\"Negro mate\",\"equivalence\":\"  \"}")
                .andExpect(jsonPath("$.equivalence").isEmpty());
        expectError(
                changing(
                        put(LISTS + "/LOV000001/values/1"),
                        "{\"label\":\"A\",\"equivalence\":\"" + "e".repeat(201) + "\"}"),
                400,
                "equivalence",
                "INVALID_EQUIVALENCE");

        changing(put(LISTS + "/LOV000001"), "{\"name\":\"Color\",\"equivalencesEnabled\":false}")
                .andExpect(jsonPath("$.equivalencesEnabled").value(false))
                .andExpect(jsonPath("$.values[0].equivalence").value("BLUE"));
    }

    private void createList(String name) throws Exception {
        changing(post(LISTS), "{\"name\":\"" + name + "\"}").andExpect(status().isCreated());
    }

    private void createLinkedAttribute(String name, String mode, String listId) throws Exception {
        changing(post("/api/v1/attributes"), """
                        {"displayName":"%s","dataType":"LIST_OF_VALUES",
                         "listOfValuesProperties":{"selectionMode":"%s","listOfValuesId":"%s"}}""".formatted(name, mode, listId)).andExpect(status().isCreated());
    }

    private ResultActions changing(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.header(PERMISSIONS, CAN_CHANGE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ResultActions expectError(ResultActions result, int status, String field, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }
}
