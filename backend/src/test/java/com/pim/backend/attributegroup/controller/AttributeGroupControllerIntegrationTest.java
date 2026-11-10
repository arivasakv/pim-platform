package com.pim.backend.attributegroup.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * API integration tests for {@code /api/v1/attribute-groups}, following
 * specs/005-attribute-groups/contracts/attribute-groups.openapi.yaml. Groups, attributes and system IDs are stored in
 * MongoDB (ADR 0003); each test starts from emptied collections.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeGroupControllerIntegrationTest {

    private static final String GROUPS = "/api/v1/attribute-groups";
    private static final String ATTRIBUTES = "/api/v1/attributes";
    private static final String PERMISSIONS = "X-Pim-Permissions";
    private static final String CAN_CHANGE = "ATTRIBUTE_CREATE";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaultConfigurations;

    AttributeGroupControllerIntegrationTest(
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

    // --- User Story 1: create ---

    @Test
    void startsWithNoGroups() throws Exception {
        mockMvc.perform(get(GROUPS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void createsAGroupWithNoAttributes() throws Exception {
        createGroupRequest("{\"name\":\"Logísticos\",\"description\":\"Dimensiones\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(GROUPS + "/" + UUID_PATTERN)))
                .andExpect(jsonPath("$.id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.name").value("Logísticos"))
                .andExpect(jsonPath("$.description").value("Dimensiones"))
                .andExpect(jsonPath("$.attributeCount").value(0))
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        mockMvc.perform(get(GROUPS))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Logísticos"))
                .andExpect(jsonPath("$.items[0].description").value("Dimensiones"))
                .andExpect(jsonPath("$.items[0].attributeCount").value(0))
                .andExpect(jsonPath("$.items[0].attributes").doesNotExist());
    }

    @Test
    void refusesADuplicateNameIgnoringCaseAndSpaces() throws Exception {
        createGroup("Logísticos");

        createGroupRequest("{\"name\":\" logísticos \"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nombre en uso"))
                .andExpect(
                        jsonPath("$.detail").value("Ya existe un grupo con el nombre \"logísticos\". Usa otro nombre."))
                .andExpect(error("name", "GROUP_NAME_IN_USE"));
        mockMvc.perform(get(GROUPS)).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void validatesTheNameAndDescription() throws Exception {
        createGroupRequest("{\"description\":\"Sin nombre\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(error("name", "REQUIRED"));
        createGroupRequest("{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("name", "INVALID_GROUP_NAME"))
                .andExpect(
                        jsonPath("$.errors[0].message").value("El nombre del grupo debe tener de 1 a 80 caracteres."));
        createGroupRequest("{\"name\":\"" + "a".repeat(81) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("name", "INVALID_GROUP_NAME"));
        createGroupRequest("{\"name\":\"" + "a".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("name", "INVALID_GROUP_NAME"));
        createGroupRequest("{\"name\":\"Logísticos\",\"description\":\"" + "d".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("description", "INVALID_GROUP_DESCRIPTION"));
        mockMvc.perform(post(GROUPS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PERMISSIONS, CAN_CHANGE)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(error("body", "REQUIRED"));
        mockMvc.perform(get(GROUPS)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void refusesToCreateWithoutThePermission() throws Exception {
        mockMvc.perform(post(GROUPS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Logísticos\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permiso para administrar grupos"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para administrar grupos de atributos."))
                .andExpect(error("permissions", "ATTRIBUTE_CREATE_NOT_PERMITTED"));
        mockMvc.perform(get(GROUPS)).andExpect(jsonPath("$.total").value(0));
    }

    // --- User Story 2: add attributes ---

    @Test
    void addsExistingAttributes() throws Exception {
        createAttributes("Peso", "Volumen", "Precio base");
        String groupId = createGroup("Logísticos");
        String before = attributeJson("ATR000001");

        addAttributes(groupId, "{\"attributeIds\":[\"ATR000001\",\"ATR000002\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(contains("ATR000001", "ATR000002")))
                .andExpect(jsonPath("$.alreadyInGroup").isEmpty())
                .andExpect(jsonPath("$.group.id").value(groupId))
                .andExpect(jsonPath("$.group.attributeCount").value(2))
                .andExpect(jsonPath("$.group.attributes[0].systemId").value("ATR000001"))
                .andExpect(jsonPath("$.group.attributes[0].displayName").value("Peso"))
                .andExpect(jsonPath("$.group.attributes[0].dataType").value("BOOLEAN"))
                .andExpect(jsonPath("$.group.attributes[1].displayName").value("Volumen"));

        addAttributes(groupId, "{\"attributeIds\":[\"atr000001\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").isEmpty())
                .andExpect(jsonPath("$.alreadyInGroup").value(contains("ATR000001")))
                .andExpect(jsonPath("$.group.attributeCount").value(2));

        assertThat(attributeJson("ATR000001")).isEqualTo(before);
    }

    @Test
    void addsNothingWhenAnAttributeIsUnknown() throws Exception {
        createAttributes("Peso", "Volumen", "Precio base");
        String groupId = createGroup("Logísticos");

        addAttributes(groupId, "{\"attributeIds\":[\"ATR000003\",\"ATR999\"]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Atributo no encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe un atributo con el ID ATR999."))
                .andExpect(error("attributeIds", "ATTRIBUTE_NOT_FOUND"));
        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(jsonPath("$.attributeCount").value(0));
    }

    @Test
    void validatesTheListOfAttributeIds() throws Exception {
        String groupId = createGroup("Logísticos");
        String hundredAndOne = IntStream.rangeClosed(1, 101)
                .mapToObj(number -> "\"ATR" + number + "\"")
                .collect(Collectors.joining(","));

        addAttributes(groupId, "{\"attributeIds\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(error("attributeIds", "INVALID_ATTRIBUTE_IDS"));
        addAttributes(groupId, "{\"attributeIds\":[" + hundredAndOne + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(error("attributeIds", "INVALID_ATTRIBUTE_IDS"));
        addAttributes(groupId, "{}").andExpect(status().isBadRequest()).andExpect(error("attributeIds", "REQUIRED"));
    }

    @Test
    void refusesToAddToAnUnknownGroupOrWithoutThePermission() throws Exception {
        createAttributes("Peso");
        String groupId = createGroup("Logísticos");

        addAttributes("unknown", "{\"attributeIds\":[\"ATR000001\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe un grupo con el ID unknown."))
                .andExpect(error("groupId", "GROUP_NOT_FOUND"));
        mockMvc.perform(post(GROUPS + "/" + groupId + "/attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attributeIds\":[\"ATR000001\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "ATTRIBUTE_CREATE_NOT_PERMITTED"));
        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(jsonPath("$.attributeCount").value(0));
    }

    // --- User Story 3: remove an attribute ---

    @Test
    void removesAnAttributeWithoutDeletingIt() throws Exception {
        createAttributes("Peso");
        String logistics = createGroup("Logísticos");
        String sales = createGroup("Compra Ventas");
        addAttributes(logistics, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());
        addAttributes(sales, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());

        removeAttribute(logistics, "ATR000001").andExpect(status().isNoContent());

        mockMvc.perform(get(GROUPS + "/" + logistics))
                .andExpect(jsonPath("$.attributeCount").value(0));
        mockMvc.perform(get(GROUPS + "/" + sales))
                .andExpect(jsonPath("$.attributeCount").value(1));
        mockMvc.perform(get(ATTRIBUTES + "/ATR000001")).andExpect(status().isOk());

        removeAttribute(logistics, "ATR000001")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El atributo ATR000001 no está en este grupo."))
                .andExpect(error("attributeId", "ATTRIBUTE_NOT_IN_GROUP"));
    }

    @Test
    void removingTheLastGroupLeavesTheAttributeUngrouped() throws Exception {
        createAttributes("Peso");
        String groupId = createGroup("Logísticos");
        addAttributes(groupId, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());

        removeAttribute(groupId, "atr000001").andExpect(status().isNoContent());

        mockMvc.perform(get(GROUPS).param("attributeId", "ATR000001"))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(ATTRIBUTES + "/ATR000001")).andExpect(status().isOk());
    }

    @Test
    void refusesToRemoveWithoutThePermissionOrFromAnUnknownGroup() throws Exception {
        createAttributes("Peso");
        String groupId = createGroup("Logísticos");
        addAttributes(groupId, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());

        mockMvc.perform(delete(GROUPS + "/" + groupId + "/attributes/ATR000001"))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "ATTRIBUTE_CREATE_NOT_PERMITTED"));
        removeAttribute("unknown", "ATR000001")
                .andExpect(status().isNotFound())
                .andExpect(error("groupId", "GROUP_NOT_FOUND"));
        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(jsonPath("$.attributeCount").value(1));
    }

    // --- User Story 4: view ---

    @Test
    void showsAGroupWithItsMembersInTheOrderAdded() throws Exception {
        createAttributes("Peso", "Volumen");
        String groupId = createGroup("Logísticos");
        addAttributes(groupId, "{\"attributeIds\":[\"ATR000002\"]}").andExpect(status().isOk());
        addAttributes(groupId, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());

        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.attributeCount").value(2))
                .andExpect(jsonPath("$.attributes[*].systemId").value(contains("ATR000002", "ATR000001")))
                .andExpect(jsonPath("$.attributes[*].displayName").value(contains("Volumen", "Peso")))
                .andExpect(jsonPath("$.attributes[*].dataType").value(contains("BOOLEAN", "BOOLEAN")));
    }

    @Test
    void anUnknownGroupIsNotFound() throws Exception {
        mockMvc.perform(get(GROUPS + "/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Grupo no encontrado"))
                .andExpect(error("groupId", "GROUP_NOT_FOUND"));
    }

    @Test
    void listsTheGroupsOfAnAttribute() throws Exception {
        createAttributes("Peso", "Volumen");
        String logistics = createGroup("Logísticos");
        String sales = createGroup("Compra Ventas");
        createGroup("Sistema");
        addAttributes(logistics, "{\"attributeIds\":[\"ATR000001\",\"ATR000002\"]}")
                .andExpect(status().isOk());
        addAttributes(sales, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());

        mockMvc.perform(get(GROUPS).param("attributeId", "atr000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(logistics, sales)));
        mockMvc.perform(get(GROUPS).param("attributeId", "ATR999"))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(GROUPS))
                .andExpect(jsonPath("$.items[*].name").value(contains("Compra Ventas", "Logísticos", "Sistema")))
                .andExpect(jsonPath("$.items[*].attributeCount").value(contains(1, 2, 0)));
    }

    // --- User Story 5: rename and delete ---

    @Test
    void renamesAGroupAndKeepsItsMembers() throws Exception {
        createAttributes("Peso");
        String groupId = createGroup("Logísticos");
        addAttributes(groupId, "{\"attributeIds\":[\"ATR000001\"]}").andExpect(status().isOk());

        updateGroup(groupId, "{\"name\":\"Logística\",\"description\":\"Peso y volumen\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.name").value("Logística"))
                .andExpect(jsonPath("$.description").value("Peso y volumen"))
                .andExpect(jsonPath("$.attributeCount").value(1));

        updateGroup(groupId, "{\"name\":\"LOGÍSTICA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("LOGÍSTICA"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void refusesToRenameToAnotherGroupsName() throws Exception {
        createGroup("Compra Ventas");
        String groupId = createGroup("Logísticos");

        updateGroup(groupId, "{\"name\":\" compra ventas \"}")
                .andExpect(status().isConflict())
                .andExpect(error("name", "GROUP_NAME_IN_USE"));
        updateGroup(groupId, "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("name", "INVALID_GROUP_NAME"));
        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(jsonPath("$.name").value("Logísticos"));
    }

    @Test
    void refusesToDeleteAGroupThatStillHasAttributes() throws Exception {
        createAttributes("Peso", "Volumen");
        String groupId = createGroup("Logísticos");
        addAttributes(groupId, "{\"attributeIds\":[\"ATR000001\",\"ATR000002\"]}")
                .andExpect(status().isOk());

        deleteGroup(groupId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Grupo con atributos"))
                .andExpect(jsonPath("$.detail")
                        .value("El grupo \"Logísticos\" tiene 2 atributos. Quítalos antes de eliminarlo."))
                .andExpect(error("groupId", "GROUP_NOT_EMPTY"));
        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(jsonPath("$.attributeCount").value(2));
    }

    @Test
    void deletesAnEmptyGroup() throws Exception {
        String groupId = createGroup("Logísticos");

        deleteGroup(groupId).andExpect(status().isNoContent());

        mockMvc.perform(get(GROUPS)).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(GROUPS + "/" + groupId)).andExpect(status().isNotFound());
    }

    @Test
    void refusesToRenameOrDeleteWithoutThePermissionOrAnUnknownGroup() throws Exception {
        String groupId = createGroup("Logísticos");

        mockMvc.perform(put(GROUPS + "/" + groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Logística\"}"))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "ATTRIBUTE_CREATE_NOT_PERMITTED"));
        mockMvc.perform(delete(GROUPS + "/" + groupId))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "ATTRIBUTE_CREATE_NOT_PERMITTED"));
        updateGroup("unknown", "{\"name\":\"Logística\"}")
                .andExpect(status().isNotFound())
                .andExpect(error("groupId", "GROUP_NOT_FOUND"));
        deleteGroup("unknown").andExpect(status().isNotFound()).andExpect(error("groupId", "GROUP_NOT_FOUND"));
        mockMvc.perform(get(GROUPS + "/" + groupId))
                .andExpect(jsonPath("$.name").value("Logísticos"));
    }

    // --- helpers ---

    private ResultActions createGroupRequest(String body) throws Exception {
        return mockMvc.perform(post(GROUPS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content(body));
    }

    private String createGroup(String name) throws Exception {
        String body = createGroupRequest("{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void createAttributes(String... names) throws Exception {
        for (String name : names) {
            mockMvc.perform(post(ATTRIBUTES)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(PERMISSIONS, CAN_CHANGE)
                            .content("{\"displayName\":\"" + name + "\",\"dataType\":\"BOOLEAN\"}"))
                    .andExpect(status().isCreated());
        }
    }

    private String attributeJson(String systemId) throws Exception {
        return mockMvc.perform(get(ATTRIBUTES + "/" + systemId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ResultActions addAttributes(String groupId, String body) throws Exception {
        return mockMvc.perform(post(GROUPS + "/" + groupId + "/attributes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content(body));
    }

    private ResultActions removeAttribute(String groupId, String attributeId) throws Exception {
        return mockMvc.perform(
                delete(GROUPS + "/" + groupId + "/attributes/" + attributeId).header(PERMISSIONS, CAN_CHANGE));
    }

    private ResultActions updateGroup(String groupId, String body) throws Exception {
        return mockMvc.perform(put(GROUPS + "/" + groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content(body));
    }

    private ResultActions deleteGroup(String groupId) throws Exception {
        return mockMvc.perform(delete(GROUPS + "/" + groupId).header(PERMISSIONS, CAN_CHANGE));
    }

    private static ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }
}
