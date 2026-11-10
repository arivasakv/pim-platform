package com.pim.backend.attribute.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.service.IdentifierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * API integration tests for {@code /api/v1/attributes}, following
 * specs/004-attribute-creation/contracts/attributes.openapi.yaml. Attributes and system IDs are in memory, so each
 * test starts a fresh context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttributeControllerIntegrationTest {

    static final String ATTRIBUTES = "/api/v1/attributes";
    static final String CAN_CREATE = "ATTRIBUTE_CREATE";

    private final MockMvc mockMvc;
    private final IdentifierService identifierService;

    AttributeControllerIntegrationTest(@Autowired MockMvc mockMvc, @Autowired IdentifierService identifierService) {
        this.mockMvc = mockMvc;
        this.identifierService = identifierService;
    }

    // --- User Story 1 ---

    @Test
    void createsABooleanAttribute() throws Exception {
        create(BOOLEAN_GIFT)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ATTRIBUTES + "/ATR000001"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.systemId").value("ATR000001"))
                .andExpect(jsonPath("$.displayName").value("Apto para regalo"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.dataType").value("BOOLEAN"))
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.textProperties").doesNotExist());

        mockMvc.perform(get(ATTRIBUTES + "/atr000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemId").value("ATR000001"))
                .andExpect(jsonPath("$.displayName").value("Apto para regalo"));
        mockMvc.perform(get("/api/v1/system-ids/ATR000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("ATTRIBUTE"));
    }

    @Test
    void answersNotFoundForAnUnknownAttribute() throws Exception {
        mockMvc.perform(get(ATTRIBUTES + "/ATR999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail", containsString("ATR999")));
    }

    @Test
    void refusesAUserWithoutThePermission() throws Exception {
        mockMvc.perform(post(ATTRIBUTES).contentType(MediaType.APPLICATION_JSON).content(BOOLEAN_GIFT))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sin permiso para crear atributos"))
                .andExpect(error("permissions", "ATTRIBUTE_CREATE_NOT_PERMITTED"));
        mockMvc.perform(get("/api/v1/system-ids/ATR000001")).andExpect(status().isNotFound());
    }

    @Test
    void validatesTheCommonFields() throws Exception {
        create("{\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("displayName", "REQUIRED"));
        create("{\"displayName\":\"X\"}").andExpect(status().isBadRequest()).andExpect(error("dataType", "REQUIRED"));
        create("{\"displayName\":\"   \",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("displayName", "INVALID_DISPLAY_NAME"));
        create("{\"displayName\":\"" + "x".repeat(121) + "\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("displayName", "INVALID_DISPLAY_NAME"));
        create("{\"displayName\":\"Peso\",\"description\":\"" + "d".repeat(1001) + "\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(error("description", "INVALID_DESCRIPTION"));
        create("{\"displayName\":\"Color\",\"dataType\":\"COLOR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("dataType", "UNKNOWN_DATA_TYPE"));
    }

    @Test
    void refusesADuplicateDisplayNameIgnoringCase() throws Exception {
        create(BOOLEAN_GIFT).andExpect(status().isCreated());

        create("{\"displayName\":\" apto PARA regalo \",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nombre en uso"))
                .andExpect(jsonPath("$.detail", containsString("apto PARA regalo")))
                .andExpect(error("displayName", "DISPLAY_NAME_IN_USE"));
        create("{\"displayName\":\"Otro\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(jsonPath("$.systemId").value("ATR000002"));
    }

    @Test
    void appliesFeature003RulesToManualIds() throws Exception {
        String color = "{\"systemId\":\"ATR-COLOR\",\"displayName\":\"Color\",\"dataType\":\"BOOLEAN\"}";

        create(color)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ID manual no permitido"))
                .andExpect(error("systemId", "MANUAL_ID_NOT_PERMITTED"))
                .andExpect(jsonPath("$.errors[0].message", containsString("ATR-COLOR")));
        createWithManualIds(color)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemId").value("ATR-COLOR"));
        createWithManualIds("{\"systemId\":\"A B\",\"displayName\":\"Talla\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("systemId", "INVALID_SYSTEM_ID"));
        createWithManualIds("{\"systemId\":\"atr-color\",\"displayName\":\"Talla\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("ID en uso"))
                .andExpect(error("systemId", "SYSTEM_ID_IN_USE"))
                .andExpect(jsonPath("$.detail", containsString("ID nuevo que no esté en uso")));
        createWithManualIds(
                        "{\"systemId\":\"" + "A".repeat(61) + "\",\"displayName\":\"Talla\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("systemId", "INVALID_SYSTEM_ID"));
    }

    @Test
    void answersConflictWhenTheAttributeSequenceIsExhausted() throws Exception {
        ObjectTypeConfiguration configuration = identifierService.findConfiguration(ObjectType.ATTRIBUTE);
        identifierService.replaceConfiguration(
                ObjectType.ATTRIBUTE,
                configuration.sequence().withNextValue(SequenceConfiguration.MAX_VALUE),
                configuration.keyFields());

        create(BOOLEAN_GIFT).andExpect(status().isCreated());
        create("{\"displayName\":\"Otro\",\"dataType\":\"BOOLEAN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Secuencia agotada"))
                .andExpect(error("objectType", "SEQUENCE_EXHAUSTED"));
    }

    // --- User Story 3 (no lists exist: the catalog is empty) ---

    @Test
    void refusesListOfValuesAttributesWhileNoListsExist() throws Exception {
        for (String body : new String[] {
            typed(
                    "Color",
                    "LIST_OF_VALUES",
                    "listOfValuesProperties",
                    "{\"selectionMode\":\"SINGLE\",\"listOfValuesId\":\"LOV-X\"}"),
            typed("Color", "LIST_OF_VALUES", "listOfValuesProperties", "{\"selectionMode\":\"SINGLE\"}"),
            "{\"displayName\":\"Color\",\"dataType\":\"LIST_OF_VALUES\"}"
        }) {
            create(body)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Lista de valores no disponible"))
                    .andExpect(error("listOfValuesProperties.listOfValuesId", "LIST_OF_VALUES_NOT_AVAILABLE"));
        }
        mockMvc.perform(get("/api/v1/system-ids/ATR000001")).andExpect(status().isNotFound());
    }

    @Test
    void refusesAnUnknownSelectionMode() throws Exception {
        create(typed("Color", "LIST_OF_VALUES", "listOfValuesProperties", "{\"selectionMode\":\"ALL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(error("listOfValuesProperties.selectionMode", "UNKNOWN_SELECTION_MODE"));
    }

    // --- User Story 5 ---

    @Test
    void ignoresAScopeAndGroupsAndAlwaysCreatesAGlobalAttribute() throws Exception {
        create("{\"displayName\":\"Apto\",\"dataType\":\"BOOLEAN\",\"scope\":\"LOCAL\",\"groups\":[\"X\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.groups").doesNotExist());
    }

    // --- User Story 4 ---

    @Test
    void createsAReferenceAttribute() throws Exception {
        create(typed(
                        "Repuesto",
                        "REFERENCE",
                        "referenceProperties",
                        "{\"targetObjectType\":\"PRODUCT\",\"cardinality\":\"MULTIPLE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceProperties.targetObjectType").value("PRODUCT"))
                .andExpect(jsonPath("$.referenceProperties.cardinality").value("MULTIPLE"));
        create(typed("Producto relacionado", "REFERENCE", "referenceProperties", "{\"targetObjectType\":\"PRODUCT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceProperties.cardinality").value("SINGLE"));
    }

    @Test
    void validatesReferenceProperties() throws Exception {
        create(typed("Repuesto", "REFERENCE", "referenceProperties", "{\"cardinality\":\"SINGLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(error("referenceProperties.targetObjectType", "REQUIRED"));
        create(typed("Repuesto", "REFERENCE", "referenceProperties", "{\"targetObjectType\":\"WIDGET\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(error("referenceProperties.targetObjectType", "UNKNOWN_TARGET_OBJECT_TYPE"));
        create(typed(
                        "Repuesto",
                        "REFERENCE",
                        "referenceProperties",
                        "{\"targetObjectType\":\"PRODUCT\",\"cardinality\":\"MANY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(error("referenceProperties.cardinality", "UNKNOWN_CARDINALITY"));
        create("{\"displayName\":\"Repuesto\",\"dataType\":\"REFERENCE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("referenceProperties", "REQUIRED"));
    }

    // --- User Story 2 ---

    @Test
    void createsATextAttributeWithItsProperties() throws Exception {
        create(
                        typed(
                                "Código",
                                "TEXT",
                                "textProperties",
                                "{\"minLength\":2,\"maxLength\":50,\"caseFormat\":\"UPPERCASE\",\"captureMask\":\"^[A-Z ]+$\",\"richText\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textProperties.minLength").value(2))
                .andExpect(jsonPath("$.textProperties.maxLength").value(50))
                .andExpect(jsonPath("$.textProperties.caseFormat").value("UPPERCASE"))
                .andExpect(jsonPath("$.textProperties.captureMask").value("^[A-Z ]+$"))
                .andExpect(jsonPath("$.textProperties.richText").value(false))
                .andExpect(jsonPath("$.numericProperties").doesNotExist());
        mockMvc.perform(get(ATTRIBUTES + "/ATR000001"))
                .andExpect(jsonPath("$.textProperties.caseFormat").value("UPPERCASE"));
    }

    @Test
    void validatesTextProperties() throws Exception {
        assertInvalid("TEXT", "textProperties", "{\"minLength\":-1}", "textProperties.minLength", "INVALID_LENGTH");
        assertInvalid("TEXT", "textProperties", "{\"maxLength\":0}", "textProperties.maxLength", "INVALID_LENGTH");
        assertInvalid("TEXT", "textProperties", "{\"maxLength\":100001}", "textProperties.maxLength", "INVALID_LENGTH");
        assertInvalid(
                "TEXT",
                "textProperties",
                "{\"minLength\":10,\"maxLength\":5}",
                "textProperties.maxLength",
                "INVALID_LENGTH_RANGE");
        assertInvalid(
                "TEXT",
                "textProperties",
                "{\"captureMask\":\"[a-\"}",
                "textProperties.captureMask",
                "INVALID_CAPTURE_MASK");
        assertInvalid(
                "TEXT",
                "textProperties",
                "{\"caseFormat\":\"TITLE\"}",
                "textProperties.caseFormat",
                "UNKNOWN_CASE_FORMAT");
    }

    @Test
    void createsNumericAttributes() throws Exception {
        create(typed(
                        "Peso neto",
                        "NUMERIC",
                        "numericProperties",
                        "{\"numberFormat\":\"DECIMAL\",\"precision\":2,\"minimum\":0,\"maximum\":999.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numericProperties.numberFormat").value("DECIMAL"))
                .andExpect(jsonPath("$.numericProperties.precision").value(2))
                .andExpect(jsonPath("$.numericProperties.minimum").value(0))
                .andExpect(jsonPath("$.numericProperties.maximum").value(999.99));
        create(typed(
                        "Piezas",
                        "NUMERIC",
                        "numericProperties",
                        "{\"numberFormat\":\"INTEGER\",\"minimum\":0,\"maximum\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numericProperties.precision").isEmpty());
    }

    @Test
    void validatesNumericProperties() throws Exception {
        assertInvalid(
                "NUMERIC",
                "numericProperties",
                "{\"numberFormat\":\"DECIMAL\"}",
                "numericProperties.precision",
                "INVALID_PRECISION");
        assertInvalid(
                "NUMERIC",
                "numericProperties",
                "{\"numberFormat\":\"INTEGER\",\"precision\":2}",
                "numericProperties.precision",
                "INVALID_PRECISION");
        assertInvalid(
                "NUMERIC",
                "numericProperties",
                "{\"numberFormat\":\"INTEGER\",\"minimum\":5,\"maximum\":1}",
                "numericProperties.maximum",
                "INVALID_VALUE_RANGE");
        assertInvalid(
                "NUMERIC",
                "numericProperties",
                "{\"numberFormat\":\"INTEGER\",\"minimum\":1.5}",
                "numericProperties.minimum",
                "INVALID_NUMBER");
        assertInvalid(
                "NUMERIC",
                "numericProperties",
                "{\"numberFormat\":\"HEX\"}",
                "numericProperties.numberFormat",
                "UNKNOWN_NUMBER_FORMAT");
        assertInvalid(
                "NUMERIC", "numericProperties", "{\"precision\":2}", "numericProperties.numberFormat", "REQUIRED");
        assertInvalid(
                "NUMERIC",
                "numericProperties",
                "{\"numberFormat\":\"INTEGER\",\"minimum\":\"abc\"}",
                "body",
                "REQUIRED");
        create("{\"displayName\":\"Peso\",\"dataType\":\"NUMERIC\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("numericProperties", "REQUIRED"));
    }

    @Test
    void createsAndValidatesDateAttributes() throws Exception {
        create(typed("Fecha de lanzamiento", "DATE_TIME", "dateTimeProperties", "{\"dateFormat\":\"DD/MM/AAAA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateTimeProperties.dateFormat").value("DD/MM/AAAA"));
        assertInvalid(
                "DATE_TIME",
                "dateTimeProperties",
                "{\"dateFormat\":\"YYYY\"}",
                "dateTimeProperties.dateFormat",
                "UNKNOWN_DATE_FORMAT");
        assertInvalid("DATE_TIME", "dateTimeProperties", "{}", "dateTimeProperties.dateFormat", "REQUIRED");
        create("{\"displayName\":\"Fecha\",\"dataType\":\"DATE_TIME\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("dateTimeProperties", "REQUIRED"));
    }

    @Test
    void createsAndValidatesCurrencyAttributes() throws Exception {
        create(typed("Precio", "CURRENCY", "currencyProperties", "{\"currencyCode\":\"MXN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currencyProperties.currencyCode").value("MXN"))
                .andExpect(jsonPath("$.currencyProperties.decimals").value(2))
                .andExpect(jsonPath("$.currencyProperties.currencySymbol", notNullValue()));
        assertInvalid(
                "CURRENCY",
                "currencyProperties",
                "{\"currencyCode\":\"XYZ\"}",
                "currencyProperties.currencyCode",
                "UNKNOWN_CURRENCY");
        assertInvalid(
                "CURRENCY",
                "currencyProperties",
                "{\"currencyCode\":\"MXN\",\"decimals\":5}",
                "currencyProperties.decimals",
                "INVALID_DECIMALS");
        assertInvalid(
                "CURRENCY", "currencyProperties", "{\"decimals\":2}", "currencyProperties.currencyCode", "REQUIRED");
        create("{\"displayName\":\"Costo\",\"dataType\":\"CURRENCY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(error("currencyProperties", "REQUIRED"));
    }

    @Test
    void ignoresPropertiesOfAnotherType() throws Exception {
        create("{\"displayName\":\"Apto\",\"dataType\":\"BOOLEAN\",\"textProperties\":{\"maxLength\":5}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textProperties").doesNotExist());
    }

    private void assertInvalid(String dataType, String propertiesName, String properties, String field, String code)
            throws Exception {
        create(typed("Atributo " + code + " " + field, dataType, propertiesName, properties))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(error(field, code));
    }

    static String typed(String name, String dataType, String propertiesName, String properties) {
        return "{\"displayName\":\"" + name + "\",\"dataType\":\"" + dataType + "\",\"" + propertiesName + "\":"
                + properties + "}";
    }

    static final String BOOLEAN_GIFT = "{\"displayName\":\"Apto para regalo\",\"dataType\":\"BOOLEAN\"}";

    ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(ATTRIBUTES)
                .header("X-Pim-Permissions", CAN_CREATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    ResultActions createWithManualIds(String body) throws Exception {
        return mockMvc.perform(post(ATTRIBUTES)
                .header("X-Pim-Permissions", CAN_CREATE)
                .header("X-Pim-Manual-Id-Permissions", "ATTRIBUTE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    static ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }
}
