package com.pim.backend.identifier.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.repository.ObjectTypeConfigurationRepository;
import java.util.List;
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

/**
 * API integration tests for {@code /api/v1/system-ids}, following
 * specs/003-identifier-management/contracts/identifiers.openapi.yaml. Identifier data is in MongoDB (ADR 0003); each
 * test starts from emptied collections with the default configurations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SystemIdControllerIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private DefaultObjectTypeConfigurations defaultConfigurations;

    /** Data is stored in MongoDB and outlives the Spring context, so each test starts from a fresh install. */
    @BeforeEach
    void startFromAFreshInstall() {
        AttributeAdministrationTestData.reset(mongoTemplate, defaultConfigurations);
    }

    static final String SYSTEM_IDS = "/api/v1/system-ids";
    static final String PERMISSIONS_HEADER = "X-Pim-Manual-Id-Permissions";

    private final MockMvc mockMvc;
    private final ObjectTypeConfigurationRepository configurations;

    SystemIdControllerIntegrationTest(
            @Autowired MockMvc mockMvc, @Autowired ObjectTypeConfigurationRepository configurations) {
        this.mockMvc = mockMvc;
        this.configurations = configurations;
    }

    // --- User Story 1: automatic IDs ---

    @Test
    void assignsAnAutomaticIdFromTheTypeSequence() throws Exception {
        assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":{}}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", SYSTEM_IDS + "/ATR000001"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.outcome").value("ASSIGNED"))
                .andExpect(jsonPath("$.registration.systemId").value("ATR000001"))
                .andExpect(jsonPath("$.registration.objectType").value("ATTRIBUTE"))
                .andExpect(jsonPath("$.registration.assignment").value("AUTOMATIC"))
                .andExpect(jsonPath("$.registration.keyFieldValues").isMap())
                .andExpect(jsonPath("$.registration.registeredAt", notNullValue()));

        assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":{}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration.systemId").value("ATR000002"));
    }

    @Test
    void readsARegisteredIdIgnoringCase() throws Exception {
        assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":{}}").andExpect(status().isCreated());

        mockMvc.perform(get(SYSTEM_IDS + "/atr000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value("ATR000001"))
                .andExpect(jsonPath("$.objectType").value("ATTRIBUTE"))
                .andExpect(jsonPath("$.assignment").value("AUTOMATIC"));
    }

    @Test
    void answersNotFoundForAnUnknownId() throws Exception {
        mockMvc.perform(get(SYSTEM_IDS + "/XYZ-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail", containsString("XYZ-1")))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void requiresTheObjectTypeAndTheKeyFieldValues() throws Exception {
        assign("{\"keyFieldValues\":{}}").andExpect(status().isBadRequest()).andExpect(error("objectType", "REQUIRED"));
        assign("{\"objectType\":\"ATTRIBUTE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("keyFieldValues", "REQUIRED"));
    }

    @Test
    void rejectsAnUnknownObjectType() throws Exception {
        assign("{\"objectType\":\"WIDGET\",\"keyFieldValues\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(error("objectType", "UNKNOWN_OBJECT_TYPE"));
    }

    @Test
    void rejectsASystemIdLongerThanSixtyCharacters() throws Exception {
        assign("{\"objectType\":\"ATTRIBUTE\",\"systemId\":\"" + "A".repeat(61) + "\",\"keyFieldValues\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(error("systemId", "INVALID_SYSTEM_ID"));
    }

    @Test
    void rejectsMoreThanTwentyKeyFieldValues() throws Exception {
        String values = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "\"field" + i + "\":\"v\"")
                .collect(Collectors.joining(",", "{", "}"));

        assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":" + values + "}")
                .andExpect(status().isBadRequest())
                .andExpect(error("keyFieldValues", "UNKNOWN_KEY_FIELD"));
    }

    @Test
    void answersConflictWhenTheSequenceIsExhausted() throws Exception {
        configurations.save(configurations
                .find(ObjectType.ATTRIBUTE)
                .withSequence(configurations
                        .find(ObjectType.ATTRIBUTE)
                        .sequence()
                        .withNextValue(SequenceConfiguration.MAX_VALUE)));

        assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":{}}").andExpect(status().isCreated());
        assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":{}}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Secuencia agotada"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(error("objectType", "SEQUENCE_EXHAUSTED"));
        mockMvc.perform(get(SYSTEM_IDS + "/ATR999999999999999999")).andExpect(status().isOk());
    }

    // --- User Story 2: manual IDs with permission ---

    @Test
    void assignsAManualIdWithThePermissionOfItsType() throws Exception {
        assign(CLASSIFICATION_ROPA, "CLASSIFICATION")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", SYSTEM_IDS + "/CLS-ROPA"))
                .andExpect(jsonPath("$.registration.systemId").value("CLS-ROPA"))
                .andExpect(jsonPath("$.registration.assignment").value("MANUAL"));
    }

    @Test
    void forbidsAManualIdWithoutThePermissionOfItsType() throws Exception {
        String body = "{\"objectType\":\"CLASSIFICATION\",\"systemId\":\"CLS-OTRA\",\"keyFieldValues\":{}}";

        for (ResultActions response : new ResultActions[] {assign(body), assign(body, "PRODUCT")}) {
            response.andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("ID manual no permitido"))
                    .andExpect(jsonPath("$.detail", containsString("CLS-OTRA")))
                    .andExpect(error("systemId", "MANUAL_ID_NOT_PERMITTED"))
                    .andExpect(jsonPath("$.errors[0].message", containsString("CLS-OTRA")));
        }
        mockMvc.perform(get(SYSTEM_IDS + "/CLS-OTRA")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAManualIdWithCharactersThatAreNotAllowed() throws Exception {
        assign("{\"objectType\":\"PRODUCT\",\"systemId\":\"A B\",\"keyFieldValues\":{}}", "PRODUCT")
                .andExpect(status().isBadRequest())
                .andExpect(error("systemId", "INVALID_SYSTEM_ID"));
    }

    @Test
    void rejectsAnIdUsedByAnObjectOfAnotherTypeIgnoringCase() throws Exception {
        assign(CLASSIFICATION_ROPA, "CLASSIFICATION").andExpect(status().isCreated());

        assign("{\"objectType\":\"PRODUCT\",\"systemId\":\"cls-ropa\",\"keyFieldValues\":{}}", "PRODUCT")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("ID en uso"))
                .andExpect(jsonPath("$.detail", containsString("cls-ropa")))
                .andExpect(jsonPath(
                        "$.detail",
                        containsString(
                                "ya se encuentra en uso por otro objeto. Favor de asignar un identificador inédito.")))
                .andExpect(error("systemId", "SYSTEM_ID_IN_USE"))
                .andExpect(jsonPath("$.errors[0].message", containsString("cls-ropa")));
    }

    @Test
    void generatesAnIdWhenTheManualIdIsBlank() throws Exception {
        assign("{\"objectType\":\"PRODUCT\",\"systemId\":\"   \",\"keyFieldValues\":{}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration.systemId").value("PRD000001"))
                .andExpect(jsonPath("$.registration.assignment").value("AUTOMATIC"));
    }

    // --- User Story 3: a saved ID never changes (FR-007) ---

    @Test
    void rejectsEveryAttemptToChangeASavedId() throws Exception {
        String registered = assign("{\"objectType\":\"ATTRIBUTE\",\"keyFieldValues\":{}}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ResultActions[] attempts = {
            mockMvc.perform(put(SYSTEM_IDS + "/ATR000001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"systemId\":\"ATR-NUEVO\"}")),
            mockMvc.perform(patch(SYSTEM_IDS + "/ATR000001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"systemId\":\"ATR-NUEVO\"}")),
            mockMvc.perform(put(SYSTEM_IDS + "/ATR000001")
                    .header(PERMISSIONS_HEADER, "ATTRIBUTE")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"systemId\":\"ATR-NUEVO\"}"))
        };
        for (ResultActions attempt : attempts) {
            attempt.andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("ID no modificable"))
                    .andExpect(jsonPath("$.detail")
                            .value("El ID de sistema ATR000001 no se puede modificar una vez guardado."))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        String afterwards = mockMvc.perform(get(SYSTEM_IDS + "/ATR000001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(registered).contains(afterwards);
        mockMvc.perform(get(SYSTEM_IDS + "/ATR-NUEVO")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAChangeOfAnUnknownIdTheSameWay() throws Exception {
        mockMvc.perform(put(SYSTEM_IDS + "/UNKNOWN-1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.detail", containsString("UNKNOWN-1")));
    }

    // --- User Story 4: reusing an existing ID (FR-009, FR-013) ---

    @Test
    void reusingTheIdOfTheSameObjectAnswersSameObject() throws Exception {
        declareAttributeKeyFields("code");

        assign(attribute("A-100", "{\"code\":\"COLOR\"}"), "ATTRIBUTE").andExpect(status().isCreated());
        assign(attribute("A-100", "{\"code\":\"COLOR\"}"), "ATTRIBUTE")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.outcome").value("SAME_OBJECT"))
                .andExpect(jsonPath("$.registration.systemId").value("A-100"))
                .andExpect(jsonPath("$.registration.keyFieldValues.code").value("COLOR"));
    }

    @Test
    void reusingTheIdWithOtherKeyValuesIsAConflict() throws Exception {
        declareAttributeKeyFields("code");
        assign(attribute("A-100", "{\"code\":\"COLOR\"}"), "ATTRIBUTE").andExpect(status().isCreated());

        assign(attribute("A-100", "{\"code\":\"TALLA\"}"), "ATTRIBUTE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath(
                        "$.detail",
                        containsString(
                                "ya se encuentra en uso por otro objeto. Favor de asignar un identificador inédito.")))
                .andExpect(error("systemId", "SYSTEM_ID_IN_USE"));
        mockMvc.perform(get(SYSTEM_IDS + "/A-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyFieldValues.code").value("COLOR"));
    }

    @Test
    void validatesTheKeyFieldValues() throws Exception {
        declareAttributeKeyFields("code");

        assign(attribute("A-100", "{}"), "ATTRIBUTE")
                .andExpect(status().isBadRequest())
                .andExpect(error("code", "MISSING_KEY_FIELD"));
        assign(attribute("A-100", "{\"code\":\"X\",\"color\":\"Y\"}"), "ATTRIBUTE")
                .andExpect(status().isBadRequest())
                .andExpect(error("color", "UNKNOWN_KEY_FIELD"));
        assign(attribute("A-100", "{\"code\":\"   \"}"), "ATTRIBUTE")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(error("code", "INVALID_KEY_FIELD_VALUE"));
        assign(attribute("A-100", "{\"code\":\"" + "x".repeat(201) + "\"}"), "ATTRIBUTE")
                .andExpect(status().isBadRequest())
                .andExpect(error("code", "INVALID_KEY_FIELD_VALUE"));
    }

    @Test
    void reusingAnIdOfATypeWithoutKeyFieldsIsAConflict() throws Exception {
        assign("{\"objectType\":\"TEMPLATE\",\"keyFieldValues\":{}}").andExpect(status().isCreated());

        assign("{\"objectType\":\"TEMPLATE\",\"systemId\":\"PLT000001\",\"keyFieldValues\":{}}", "TEMPLATE")
                .andExpect(status().isConflict())
                .andExpect(error("systemId", "SYSTEM_ID_IN_USE"));
    }

    private void declareAttributeKeyFields(String... names) {
        ObjectTypeConfiguration attribute = configurations.find(ObjectType.ATTRIBUTE);
        configurations.save(new ObjectTypeConfiguration(
                ObjectType.ATTRIBUTE, attribute.sequence(), new KeyFieldDeclaration(List.of(names))));
    }

    private static String attribute(String systemId, String keyFieldValues) {
        return "{\"objectType\":\"ATTRIBUTE\",\"systemId\":\"" + systemId + "\",\"keyFieldValues\":" + keyFieldValues
                + "}";
    }

    static final String CLASSIFICATION_ROPA =
            "{\"objectType\":\"CLASSIFICATION\",\"systemId\":\"CLS-ROPA\",\"keyFieldValues\":{}}";

    ResultActions assign(String body) throws Exception {
        return mockMvc.perform(
                post(SYSTEM_IDS).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions assign(String body, String permissions) throws Exception {
        return mockMvc.perform(post(SYSTEM_IDS)
                .header(PERMISSIONS_HEADER, permissions)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    static org.springframework.test.web.servlet.ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }
}
