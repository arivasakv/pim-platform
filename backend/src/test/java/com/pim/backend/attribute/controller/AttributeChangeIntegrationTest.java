package com.pim.backend.attribute.controller;

import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.ATTRIBUTES;
import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.CAN_CREATE;
import static com.pim.backend.attribute.controller.AttributeControllerIntegrationTest.error;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Consulting, editing and deleting attributes through the API (feature 007; contract v1.2.0), with the real group
 * and list services on the local test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeChangeIntegrationTest {

    private static final String GROUPS = "/api/v1/attribute-groups";
    private static final String LISTS = "/api/v1/lists-of-values";
    private static final String PESO =
            "{\"displayName\":\"Peso\",\"dataType\":\"NUMERIC\",\"numericProperties\":{\"numberFormat\":\"DECIMAL\",\"precision\":2}}";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaultConfigurations;

    AttributeChangeIntegrationTest(
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

    // --- User Story 1: consult ---

    @Test
    void listsGroupsListNamesAndNoChangeDate() throws Exception {
        String list = createList("Color");
        String peso = createAttribute(PESO);
        String color = createAttribute(listOfValues("Color", "MULTIPLE", list));
        String logisticos = createGroup("Logísticos");
        String ventas = createGroup("Ventas");
        addToGroup(ventas, peso);
        addToGroup(logisticos, peso);

        mockMvc.perform(get(ATTRIBUTES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].displayName").value(contains("Color", "Peso")))
                .andExpect(jsonPath("$.items[0].listOfValuesProperties.listOfValuesId")
                        .value(list))
                .andExpect(jsonPath("$.items[0].listOfValuesProperties.listOfValuesName")
                        .value("Color"))
                .andExpect(jsonPath("$.items[0].groups").isEmpty())
                .andExpect(jsonPath("$.items[1].groups[*].name").value(contains("Logísticos", "Ventas")))
                .andExpect(jsonPath("$.items[1].groups[0].id").value(logisticos))
                .andExpect(jsonPath("$.items[1].updatedAt").doesNotExist());
        mockMvc.perform(get(ATTRIBUTES + "/" + color.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listOfValuesProperties.listOfValuesName").value("Color"));
        mockMvc.perform(get(ATTRIBUTES + "/" + peso))
                .andExpect(jsonPath("$.groups[*].name").value(contains("Logísticos", "Ventas")));
    }

    // --- User Story 2: edit ---

    @Test
    void editsAnAttributeAndKeepsItsIdTypeCreationDateAndGroups() throws Exception {
        String peso = createAttribute(PESO);
        String group = createGroup("Logísticos");
        addToGroup(group, peso);
        String createdAt = JsonPath.read(attributeJson(peso), "$.createdAt");

        update(
                        peso,
                        "{\"displayName\":\"Peso neto\",\"description\":\"Sin empaque\",\"dataType\":\"NUMERIC\","
                                + "\"numericProperties\":{\"numberFormat\":\"DECIMAL\",\"precision\":3,\"minimum\":\"0\"}}",
                        CAN_CREATE)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.systemId").value(peso))
                .andExpect(jsonPath("$.displayName").value("Peso neto"))
                .andExpect(jsonPath("$.description").value("Sin empaque"))
                .andExpect(jsonPath("$.dataType").value("NUMERIC"))
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.numericProperties.precision").value(3))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.groups[0].name").value("Logísticos"));

        mockMvc.perform(get(GROUPS + "/" + group))
                .andExpect(jsonPath("$.attributes[0].displayName").value("Peso neto"));
        // The same name in other letter case is the attribute's own name.
        update(peso, peso("PESO NETO", 3), CAN_CREATE).andExpect(status().isOk());
    }

    @Test
    void refusesInvalidEditsWithFeature004CodesAndChangesNothing() throws Exception {
        String peso = createAttribute(PESO);
        createAttribute("{\"displayName\":\"Color\",\"dataType\":\"BOOLEAN\"}");
        String before = attributeJson(peso);

        update(peso, peso("Peso", 0), CAN_CREATE)
                .andExpect(status().isBadRequest())
                .andExpect(error("numericProperties.precision", "INVALID_PRECISION"));
        update(peso, "{\"dataType\":\"NUMERIC\",\"numericProperties\":{\"numberFormat\":\"INTEGER\"}}", CAN_CREATE)
                .andExpect(status().isBadRequest())
                .andExpect(error("displayName", "REQUIRED"));
        update(peso, peso(" color ", 2), CAN_CREATE)
                .andExpect(status().isConflict())
                .andExpect(error("displayName", "DISPLAY_NAME_IN_USE"));
        update(peso, "{\"displayName\":\"Peso\",\"dataType\":\"BOOLEAN\"}", CAN_CREATE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El tipo de dato no se puede cambiar."))
                .andExpect(error("dataType", "DATA_TYPE_IMMUTABLE"));
        update(
                        peso,
                        "{\"systemId\":\"OTRO1\",\"displayName\":\"Peso\",\"dataType\":\"NUMERIC\","
                                + "\"numericProperties\":{\"numberFormat\":\"INTEGER\"}}",
                        CAN_CREATE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("systemId", "SYSTEM_ID_IMMUTABLE"));
        update(peso, peso("Peso", 3), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permiso para modificar atributos."))
                .andExpect(error("permissions", "ATTRIBUTE_CHANGE_NOT_PERMITTED"));
        update("ATR999", peso("Peso", 3), CAN_CREATE).andExpect(status().isNotFound());

        assertThat(attributeJson(peso)).isEqualTo(before);
    }

    @Test
    void relinksAListOfValuesAttributeAndBothListsShowIt() throws Exception {
        String color = createList("Color");
        String talla = createList("Talla");
        String attribute = createAttribute(listOfValues("Medida", "MULTIPLE", color));

        update(attribute, listOfValues("Medida", "SINGLE", talla), CAN_CREATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listOfValuesProperties.selectionMode").value("SINGLE"))
                .andExpect(jsonPath("$.listOfValuesProperties.listOfValuesName").value("Talla"));
        mockMvc.perform(get(LISTS + "/" + color))
                .andExpect(jsonPath("$.attributes").isEmpty());
        mockMvc.perform(get(LISTS + "/" + talla))
                .andExpect(jsonPath("$.attributes[0].systemId").value(attribute));
        update(attribute, listOfValues("Medida", "SINGLE", "LOV999999"), CAN_CREATE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("listOfValuesProperties.listOfValuesId", "LIST_OF_VALUES_NOT_FOUND"));
    }

    // --- User Story 3: delete ---

    @Test
    void deletesAnUngroupedAttribute() throws Exception {
        String peso = createAttribute(PESO);

        mockMvc.perform(delete(ATTRIBUTES + "/" + peso.toLowerCase()).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ATTRIBUTES + "/" + peso)).andExpect(status().isNotFound());
        mockMvc.perform(delete(ATTRIBUTES + "/" + peso).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesADeletionWithoutThePermission() throws Exception {
        String peso = createAttribute(PESO);

        mockMvc.perform(delete(ATTRIBUTES + "/" + peso))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "ATTRIBUTE_CHANGE_NOT_PERMITTED"));
        mockMvc.perform(get(ATTRIBUTES + "/" + peso)).andExpect(status().isOk());
    }

    @Test
    void refusesToDeleteAnAttributeInGroupsUntilItIsRemovedFromThem() throws Exception {
        String peso = createAttribute(PESO);
        String logisticos = createGroup("Logísticos");
        String ventas = createGroup("Ventas");
        addToGroup(logisticos, peso);
        addToGroup(ventas, peso);

        mockMvc.perform(delete(ATTRIBUTES + "/" + peso).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El atributo pertenece a 2 grupos: Logísticos, Ventas. "
                                + "Quítalo de esos grupos antes de eliminarlo."))
                .andExpect(error("groups", "ATTRIBUTE_IN_GROUPS"))
                .andExpect(jsonPath("$.groups[*].name").value(contains("Logísticos", "Ventas")))
                .andExpect(jsonPath("$.groups[0].id").value(logisticos));
        mockMvc.perform(get(ATTRIBUTES + "/" + peso)).andExpect(status().isOk());

        removeFromGroup(logisticos, peso);
        mockMvc.perform(delete(ATTRIBUTES + "/" + peso).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El atributo pertenece a 1 grupo: Ventas. Quítalo de esos grupos antes de eliminarlo."));
        removeFromGroup(ventas, peso);
        mockMvc.perform(delete(ATTRIBUTES + "/" + peso).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(GROUPS + "/" + ventas)).andExpect(status().isOk());
    }

    @Test
    void deletingTheLastAttributeOfAListLetsTheListBeDeleted() throws Exception {
        String list = createList("Color");
        mockMvc.perform(post(LISTS + "/" + list + "/values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .content("{\"id\":\"0001\",\"label\":\"Azul\"}"))
                .andExpect(status().isCreated());
        String color = createAttribute(listOfValues("Color", "SINGLE", list));
        mockMvc.perform(delete(LISTS + "/" + list).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(ATTRIBUTES + "/" + color).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LISTS + "/" + list))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values[0].label").value("Azul"))
                .andExpect(jsonPath("$.attributes").isEmpty());
        mockMvc.perform(delete(LISTS + "/" + list).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNoContent());
    }

    @Test
    void neverGivesADeletedAttributesIdToAnotherAttribute() throws Exception {
        String peso = createAttribute(PESO);
        mockMvc.perform(delete(ATTRIBUTES + "/" + peso).header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNoContent());

        assertThat(createAttribute("{\"displayName\":\"Nuevo\",\"dataType\":\"BOOLEAN\"}"))
                .isNotEqualTo(peso);
        mockMvc.perform(post(ATTRIBUTES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .header("X-Pim-Manual-Id-Permissions", "ATTRIBUTE")
                        .content("{\"systemId\":\"" + peso + "\",\"displayName\":\"Manual\",\"dataType\":\"BOOLEAN\"}"))
                .andExpect(status().isConflict());
    }

    private String createAttribute(String body) throws Exception {
        String response = mockMvc.perform(post(ATTRIBUTES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.systemId");
    }

    private String createList(String name) throws Exception {
        String response = mockMvc.perform(post(LISTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.systemId");
    }

    private String createGroup(String name) throws Exception {
        String response = mockMvc.perform(post(GROUPS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void addToGroup(String groupId, String attributeId) throws Exception {
        mockMvc.perform(post(GROUPS + "/" + groupId + "/attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Pim-Permissions", CAN_CREATE)
                        .content("{\"attributeIds\":[\"" + attributeId + "\"]}"))
                .andExpect(status().isOk());
    }

    private void removeFromGroup(String groupId, String attributeId) throws Exception {
        mockMvc.perform(delete(GROUPS + "/" + groupId + "/attributes/" + attributeId)
                        .header("X-Pim-Permissions", CAN_CREATE))
                .andExpect(status().isNoContent());
    }

    private ResultActions update(String systemId, String body, String permissions) throws Exception {
        var request = put(ATTRIBUTES + "/" + systemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (permissions != null) {
            request.header("X-Pim-Permissions", permissions);
        }
        return mockMvc.perform(request);
    }

    private String attributeJson(String systemId) throws Exception {
        return mockMvc.perform(get(ATTRIBUTES + "/" + systemId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String peso(String name, int precision) {
        return "{\"displayName\":\"" + name + "\",\"dataType\":\"NUMERIC\",\"numericProperties\":"
                + "{\"numberFormat\":\"DECIMAL\",\"precision\":" + precision + "}}";
    }

    private static String listOfValues(String name, String mode, String listId) {
        return "{\"displayName\":\"" + name + "\",\"dataType\":\"LIST_OF_VALUES\",\"listOfValuesProperties\":"
                + "{\"selectionMode\":\"" + mode + "\",\"listOfValuesId\":\"" + listId + "\"}}";
    }
}
