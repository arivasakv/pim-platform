package com.pim.backend.taxonomy.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
 * API integration tests for {@code /api/v1/taxonomy-nodes}, following
 * specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml, and for the attribute deletion check of
 * FR-021. Nodes, attributes and system IDs are stored in MongoDB (ADR 0003); each test starts from emptied
 * collections.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaxonomyNodeControllerIntegrationTest {

    private static final String NODES = "/api/v1/taxonomy-nodes";
    private static final String ATTRIBUTES = "/api/v1/attributes";
    private static final String PERMISSIONS = "X-Pim-Permissions";
    private static final String CAN_CHANGE = "ATTRIBUTE_CREATE";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaultConfigurations;

    TaxonomyNodeControllerIntegrationTest(
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

    // --- User Story 1: hierarchy ---

    @Test
    void startsEmptyAndCreatesTopLevelAndChildNodes() throws Exception {
        mockMvc.perform(get(NODES))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));

        createRequest("{\"name\":\" Ropa \",\"description\":\"Prendas\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", NODES + "/CLS000001"))
                .andExpect(jsonPath("$.systemId").value("CLS000001"))
                .andExpect(jsonPath("$.name").value("Ropa"))
                .andExpect(jsonPath("$.description").value("Prendas"))
                .andExpect(jsonPath("$.parentId").value(nullValue()))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.path").isEmpty())
                .andExpect(jsonPath("$.children").isEmpty())
                .andExpect(jsonPath("$.directAttributeCount").value(0))
                .andExpect(jsonPath("$.effectiveAttributes").isEmpty())
                .andExpect(jsonPath("$.createdAt", notNullValue()));
        createRequest("{\"name\":\"Ropa de hombre\",\"parentId\":\"cls000001\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value("CLS000001"))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.path[0].systemId").value("CLS000001"))
                .andExpect(jsonPath("$.path[0].name").value("Ropa"));

        mockMvc.perform(get(NODES))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].name", contains("Ropa", "Ropa de hombre")))
                .andExpect(jsonPath("$.items[0].childCount").value(1))
                .andExpect(jsonPath("$.items[1].level").value(2))
                .andExpect(jsonPath("$.items[1].parentId").value("CLS000001"))
                .andExpect(jsonPath("$.items[1].directAttributeCount").value(0));
        mockMvc.perform(get(NODES + "/CLS000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].name").value("Ropa de hombre"));
    }

    @Test
    void refusesInvalidRequestsWithTheContractCodes() throws Exception {
        createRequest("{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("name", "INVALID_NODE_NAME"));
        createRequest("{}").andExpect(status().isBadRequest()).andExpect(error("name", "REQUIRED"));
        createRequest("{\"name\":\"Ropa\",\"description\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("description", "INVALID_NODE_DESCRIPTION"));
        createRequest("{\"name\":\"Camisas\",\"parentId\":\"CLS999999\"}")
                .andExpect(status().isNotFound())
                .andExpect(error("parentId", "NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No existe un nodo con el ID CLS999999."));
        mockMvc.perform(post(NODES).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ropa\"}"))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "TAXONOMY_CHANGE_NOT_PERMITTED"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para administrar la taxonomía."));
        mockMvc.perform(get(NODES + "/CLS999999"))
                .andExpect(status().isNotFound())
                .andExpect(error("nodeId", "NODE_NOT_FOUND"));
        mockMvc.perform(get(NODES)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void namesAreUniqueAmongSiblings() throws Exception {
        String ropa = createNode("Ropa", null);
        createNode("Accesorios", ropa);
        String calzado = createNode("Calzado", null);

        createRequest("{\"name\":\" ropa \"}")
                .andExpect(status().isConflict())
                .andExpect(error("name", "NODE_NAME_IN_USE"))
                .andExpect(jsonPath("$.detail").value("Ya existe un nodo «ropa» en este nivel. Usa otro nombre."));
        createRequest("{\"name\":\"Accesorios\",\"parentId\":\"" + calzado + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void updatesAndDeletesNodes() throws Exception {
        String ropa = createNode("Ropa", null);
        String camisas = createNode("Camisas", ropa);

        update(camisas, "{\"name\":\"Camisas de vestir\",\"description\":\"Formales\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camisas de vestir"))
                .andExpect(jsonPath("$.description").value("Formales"))
                .andExpect(jsonPath("$.parentId").value(ropa))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
        deleteNode(ropa)
                .andExpect(status().isConflict())
                .andExpect(error("nodeId", "NODE_HAS_CHILDREN"))
                .andExpect(jsonPath("$.title").value("Nodo con nodos hijos"));
        deleteNode(camisas).andExpect(status().isNoContent());
        deleteNode(ropa).andExpect(status().isNoContent());
        mockMvc.perform(get(NODES)).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(delete(NODES + "/" + ropa)).andExpect(status().isForbidden());
    }

    // --- User Stories 2 and 3: assignments and effective attributes ---

    @Test
    void returnsTheFeatureRequestExample() throws Exception {
        createAttributes("Color", "Material", "Marca", "Género", "Largo de manga", "Talla de cintura");
        String ropa = createNode("Ropa", null);
        String hombre = createNode("Ropa de hombre", ropa);
        String camisas = createNode("Camisas", hombre);
        String pantalones = createNode("Pantalones", hombre);

        assign(ropa, "ATR000001", "ATR000002", "ATR000003")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", contains("ATR000001", "ATR000002", "ATR000003")))
                .andExpect(jsonPath("$.alreadyAssigned").isEmpty())
                .andExpect(jsonPath("$.node.directAttributeCount").value(3));
        assign(hombre, "ATR000004").andExpect(status().isOk());
        assign(camisas, "ATR000005").andExpect(status().isOk());
        assign(pantalones, "ATR000006").andExpect(status().isOk());

        mockMvc.perform(get(NODES + "/" + camisas))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.path[*].name", contains("Ropa", "Ropa de hombre")))
                .andExpect(jsonPath(
                        "$.effectiveAttributes[*].displayName",
                        contains("Color", "Material", "Marca", "Género", "Largo de manga")))
                .andExpect(jsonPath(
                        "$.effectiveAttributes[*].assignment",
                        contains("INHERITED", "INHERITED", "INHERITED", "INHERITED", "DIRECT")))
                .andExpect(jsonPath(
                        "$.effectiveAttributes[*].origin.name",
                        contains("Ropa", "Ropa", "Ropa", "Ropa de hombre", "Camisas")))
                .andExpect(jsonPath("$.effectiveAttributes[0].systemId").value("ATR000001"))
                .andExpect(jsonPath("$.effectiveAttributes[0].dataType").value("BOOLEAN"))
                .andExpect(jsonPath("$.effectiveAttributes[0].origin.systemId").value(ropa))
                .andExpect(
                        jsonPath("$.effectiveAttributes[4].alsoInheritedFrom").value(nullValue()));
        mockMvc.perform(get(NODES + "/" + pantalones))
                .andExpect(jsonPath(
                        "$.effectiveAttributes[*].displayName",
                        contains("Color", "Material", "Marca", "Género", "Talla de cintura")));
    }

    @Test
    void reportsRepeatsUnknownAttributesAndInheritedRemovals() throws Exception {
        createAttributes("Color", "Género");
        String hombre = createNode("Ropa de hombre", null);
        String camisas = createNode("Camisas", hombre);
        assign(hombre, "ATR000001", "ATR000002").andExpect(status().isOk());

        assign(hombre, "atr000001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").isEmpty())
                .andExpect(jsonPath("$.alreadyAssigned", contains("ATR000001")));
        assign(hombre, "ATR999999")
                .andExpect(status().isUnprocessableContent())
                .andExpect(error("attributeIds", "ATTRIBUTE_NOT_FOUND"));
        mockMvc.perform(post(NODES + "/" + hombre + "/attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PERMISSIONS, CAN_CHANGE)
                        .content("{\"attributeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(error("attributeIds", "INVALID_ATTRIBUTE_IDS"));
        removeAttribute(camisas, "ATR000001")
                .andExpect(status().isConflict())
                .andExpect(error("attributeId", "ATTRIBUTE_NOT_DIRECT"))
                .andExpect(jsonPath("$.detail")
                        .value("El atributo «Color» se hereda de «Ropa de hombre». Quítalo en ese nodo."));

        assign(camisas, "ATR000002").andExpect(status().isOk());
        mockMvc.perform(get(NODES + "/" + camisas))
                .andExpect(jsonPath("$.effectiveAttributes[*].displayName", contains("Color", "Género")))
                .andExpect(jsonPath("$.effectiveAttributes[1].assignment").value("DIRECT"))
                .andExpect(jsonPath("$.effectiveAttributes[1].alsoInheritedFrom.name")
                        .value("Ropa de hombre"));

        removeAttribute(hombre, "ATR000001").andExpect(status().isNoContent());
        removeAttribute(hombre, "ATR000001")
                .andExpect(status().isNotFound())
                .andExpect(error("attributeId", "ATTRIBUTE_NOT_ON_NODE"));
        deleteNode(hombre).andExpect(status().isConflict()).andExpect(error("nodeId", "NODE_HAS_CHILDREN"));
    }

    @Test
    void showsTheCurrentNameOfARenamedAttribute() throws Exception {
        createAttributes("Color");
        String ropa = createNode("Ropa", null);
        assign(ropa, "ATR000001").andExpect(status().isOk());

        mockMvc.perform(put(ATTRIBUTES + "/ATR000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PERMISSIONS, CAN_CHANGE)
                        .content("{\"displayName\":\"Color principal\",\"dataType\":\"BOOLEAN\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(NODES + "/" + ropa))
                .andExpect(jsonPath("$.effectiveAttributes[0].displayName").value("Color principal"));
    }

    // --- FR-021: attribute deletion ---

    @Test
    void refusesToDeleteAnAttributeAssignedToNodes() throws Exception {
        createAttributes("Color");
        String ropa = createNode("Ropa", null);
        assign(ropa, "ATR000001").andExpect(status().isOk());

        mockMvc.perform(delete(ATTRIBUTES + "/ATR000001").header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isConflict())
                .andExpect(error("nodes", "ATTRIBUTE_ON_TAXONOMY_NODES"))
                .andExpect(jsonPath("$.detail")
                        .value("El atributo pertenece a 1 nodo de la taxonomía: Ropa. "
                                + "Quítalo de esos nodos antes de eliminarlo."))
                .andExpect(jsonPath("$.nodes[0].systemId").value(ropa))
                .andExpect(jsonPath("$.nodes[0].name").value("Ropa"));
        mockMvc.perform(get(ATTRIBUTES + "/ATR000001")).andExpect(status().isOk());

        removeAttribute(ropa, "ATR000001").andExpect(status().isNoContent());
        mockMvc.perform(delete(ATTRIBUTES + "/ATR000001").header(PERMISSIONS, CAN_CHANGE))
                .andExpect(status().isNoContent());
    }

    // --- Feature 011: moving nodes ---

    @Test
    void movesANodeWithItsBranchAndPersistsTheNewParent() throws Exception {
        createAttributes("Color", "Género", "Largo de manga", "Material de suela");
        String ropa = createNode("Ropa", null);
        String hombre = createNode("Ropa de hombre", ropa);
        String camisas = createNode("Camisas", hombre);
        String formales = createNode("Camisas formales", camisas);
        String calzado = createNode("Calzado", null);
        assign(ropa, "ATR000001").andExpect(status().isOk());
        assign(hombre, "ATR000002").andExpect(status().isOk());
        assign(camisas, "ATR000003").andExpect(status().isOk());
        assign(calzado, "ATR000004").andExpect(status().isOk());

        move(camisas, "\"" + calzado.toLowerCase() + "\"")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.movedNodeCount").value(2))
                .andExpect(jsonPath("$.node.systemId").value(camisas))
                .andExpect(jsonPath("$.node.parentId").value(calzado))
                .andExpect(jsonPath("$.node.level").value(2))
                .andExpect(jsonPath("$.node.path[*].name", contains("Calzado")))
                .andExpect(jsonPath("$.node.children[*].name", contains("Camisas formales")))
                .andExpect(jsonPath("$.node.directAttributeCount").value(1))
                .andExpect(jsonPath("$.node.updatedAt", notNullValue()));

        mockMvc.perform(get(NODES + "/" + formales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.path[*].name", contains("Calzado", "Camisas")))
                .andExpect(jsonPath(
                        "$.effectiveAttributes[*].displayName", contains("Material de suela", "Largo de manga")))
                .andExpect(jsonPath("$.effectiveAttributes[0].origin.name").value("Calzado"))
                .andExpect(jsonPath("$.effectiveAttributes[1].origin.name").value("Camisas"));
        mockMvc.perform(get(NODES + "/" + hombre))
                .andExpect(jsonPath("$.children").isEmpty())
                .andExpect(jsonPath("$.effectiveAttributes[*].displayName", contains("Color", "Género")));
    }

    @Test
    void movesANodeToTheTopLevel() throws Exception {
        String ropa = createNode("Ropa", null);
        String mujer = createNode("Ropa de mujer", ropa);
        createNode("Blusas", mujer);

        move(mujer, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedNodeCount").value(2))
                .andExpect(jsonPath("$.node.parentId").value(nullValue()))
                .andExpect(jsonPath("$.node.level").value(1))
                .andExpect(jsonPath("$.node.path").isEmpty());
        mockMvc.perform(get(NODES)).andExpect(jsonPath("$.items[?(@.name == 'Blusas')].level", contains(2)));

        mockMvc.perform(post(NODES + "/" + mujer + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PERMISSIONS, CAN_CHANGE)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(error("parentId", "NODE_ALREADY_THERE"))
                .andExpect(jsonPath("$.title").value("Movimiento sin efecto"))
                .andExpect(jsonPath("$.detail").value("«Ropa de mujer» ya está en el primer nivel."));
    }

    @Test
    void refusesMovesThatWouldBreakTheHierarchyAndChangesNothing() throws Exception {
        String ropa = createNode("Ropa", null);
        String hombre = createNode("Ropa de hombre", ropa);
        String camisas = createNode("Camisas", hombre);
        String mujer = createNode("Ropa de mujer", ropa);
        createNode("Camisas", mujer);

        move(hombre, "\"" + camisas + "\"")
                .andExpect(status().isConflict())
                .andExpect(error("parentId", "NODE_MOVE_INTO_OWN_BRANCH"))
                .andExpect(jsonPath("$.title").value("Movimiento no válido"))
                .andExpect(jsonPath("$.detail")
                        .value("No puedes mover «Ropa de hombre» dentro de sí mismo ni de uno de sus nodos"
                                + " descendientes."));
        move(camisas, "\"" + hombre + "\"")
                .andExpect(status().isConflict())
                .andExpect(error("parentId", "NODE_ALREADY_THERE"))
                .andExpect(jsonPath("$.detail").value("«Camisas» ya está en «Ropa de hombre»."));
        move(camisas, "\"" + mujer + "\"")
                .andExpect(status().isConflict())
                .andExpect(error("parentId", "NODE_NAME_IN_USE"))
                .andExpect(jsonPath("$.title").value("Nombre en uso"))
                .andExpect(jsonPath("$.detail")
                        .value("Ya existe un nodo «Camisas» en «Ropa de mujer». Cambia el nombre de uno de los dos"
                                + " antes de mover."));
        move(camisas, "\"CLS999999\"")
                .andExpect(status().isNotFound())
                .andExpect(error("parentId", "NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No existe un nodo con el ID CLS999999."));
        move("CLS999999", "\"" + mujer + "\"")
                .andExpect(status().isNotFound())
                .andExpect(error("nodeId", "NODE_NOT_FOUND"));
        move(camisas, "\"" + "X".repeat(41) + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(error("parentId", "NODE_NOT_FOUND"));
        mockMvc.perform(post(NODES + "/" + camisas + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + mujer + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(error("permissions", "TAXONOMY_CHANGE_NOT_PERMITTED"));

        mockMvc.perform(get(NODES + "/" + camisas))
                .andExpect(jsonPath("$.parentId").value(hombre))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));
    }

    // --- helpers ---

    private ResultActions move(String nodeId, String parentJson) throws Exception {
        return mockMvc.perform(post(NODES + "/" + nodeId + "/move")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content("{\"parentId\":" + parentJson + "}"));
    }

    private ResultActions createRequest(String body) throws Exception {
        return mockMvc.perform(post(NODES)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content(body));
    }

    private String createNode(String name, String parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        String body = createRequest("{\"name\":\"" + name + "\"" + parent + "}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.systemId");
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

    private ResultActions assign(String nodeId, String... attributeIds) throws Exception {
        String ids = Stream.of(attributeIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return mockMvc.perform(post(NODES + "/" + nodeId + "/attributes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content("{\"attributeIds\":[" + ids + "]}"));
    }

    private ResultActions removeAttribute(String nodeId, String attributeId) throws Exception {
        return mockMvc.perform(
                delete(NODES + "/" + nodeId + "/attributes/" + attributeId).header(PERMISSIONS, CAN_CHANGE));
    }

    private ResultActions update(String nodeId, String body) throws Exception {
        return mockMvc.perform(put(NODES + "/" + nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PERMISSIONS, CAN_CHANGE)
                .content(body));
    }

    private ResultActions deleteNode(String nodeId) throws Exception {
        return mockMvc.perform(delete(NODES + "/" + nodeId).header(PERMISSIONS, CAN_CHANGE));
    }

    private static ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }
}
