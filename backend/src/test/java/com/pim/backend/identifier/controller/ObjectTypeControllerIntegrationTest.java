package com.pim.backend.identifier.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * API integration tests for {@code /api/v1/object-types} (User Story 5; FR-006, FR-008, FR-012, FR-013), following
 * specs/003-identifier-management/contracts/identifiers.openapi.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ObjectTypeControllerIntegrationTest {

    private static final String OBJECT_TYPES = "/api/v1/object-types";
    private static final String PRODUCT_NUMERIC =
            "{\"sequence\":{\"kind\":\"NUMERIC\",\"minimumDigits\":8,\"nextValue\":1000},\"keyFields\":[]}";

    private final MockMvc mockMvc;

    ObjectTypeControllerIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void listsTheSixTypesWithTheirDefaults() throws Exception {
        mockMvc.perform(get(OBJECT_TYPES))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[*].objectType")
                        .value(contains(
                                "PRODUCT", "ATTRIBUTE", "LIST_OF_VALUES", "TEMPLATE", "CLASSIFICATION", "INTERFACE")))
                .andExpect(jsonPath("$.items[*].sequence.prefix")
                        .value(contains("PRD", "ATR", "LOV", "PLT", "CLS", "INT")))
                .andExpect(jsonPath("$.items[*].sequence.kind").value(everyItem(is("ALPHANUMERIC"))))
                .andExpect(jsonPath("$.items[*].sequence.minimumDigits").value(everyItem(is(6))))
                .andExpect(jsonPath("$.items[*].sequence.nextValue").value(everyItem(is(1))))
                .andExpect(jsonPath("$.items[0].keyFields").isEmpty());
    }

    @Test
    void readsOneType() throws Exception {
        mockMvc.perform(get(OBJECT_TYPES + "/TEMPLATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("TEMPLATE"))
                .andExpect(jsonPath("$.sequence.prefix").value("PLT"));
    }

    @Test
    void answersNotFoundForAnUnknownType() throws Exception {
        mockMvc.perform(get(OBJECT_TYPES + "/WIDGET"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail", containsString("WIDGET")));
        replace("WIDGET", PRODUCT_NUMERIC).andExpect(status().isNotFound());
    }

    @Test
    void replacesAConfigurationAndLaterIdsFollowIt() throws Exception {
        replace("PRODUCT", PRODUCT_NUMERIC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("PRODUCT"))
                .andExpect(jsonPath("$.sequence.kind").value("NUMERIC"))
                .andExpect(jsonPath("$.sequence.minimumDigits").value(8))
                .andExpect(jsonPath("$.sequence.nextValue").value(1000))
                .andExpect(jsonPath("$.keyFields").isEmpty());

        mockMvc.perform(post("/api/v1/system-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectType\":\"PRODUCT\",\"keyFieldValues\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration.systemId").value("00001000"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "{\"kind\":\"HEXADECIMAL\",\"minimumDigits\":6,\"nextValue\":1}|sequence.kind|INVALID_SEQUENCE_KIND",
                "{\"kind\":\"NUMERIC\",\"prefix\":\"PRD\",\"minimumDigits\":6,\"nextValue\":1}|sequence.prefix|INVALID_PREFIX",
                "{\"kind\":\"ALPHANUMERIC\",\"minimumDigits\":6,\"nextValue\":1}|sequence.prefix|INVALID_PREFIX",
                "{\"kind\":\"ALPHANUMERIC\",\"prefix\":\"prd\",\"minimumDigits\":6,\"nextValue\":1}|sequence.prefix|INVALID_PREFIX",
                "{\"kind\":\"NUMERIC\",\"minimumDigits\":0,\"nextValue\":1}|sequence.minimumDigits|INVALID_MINIMUM_DIGITS",
                "{\"kind\":\"NUMERIC\",\"minimumDigits\":19,\"nextValue\":1}|sequence.minimumDigits|INVALID_MINIMUM_DIGITS",
                "{\"kind\":\"NUMERIC\",\"minimumDigits\":6,\"nextValue\":0}|sequence.nextValue|INVALID_NEXT_VALUE",
                "{\"minimumDigits\":6,\"nextValue\":1}|sequence.kind|REQUIRED"
            })
    void rejectsAnInvalidSequence(String sequence, String field, String code) throws Exception {
        replace("PRODUCT", "{\"sequence\":" + sequence + ",\"keyFields\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(error(field, code));
    }

    @Test
    void rejectsInvalidKeyFieldNames() throws Exception {
        String twentyOne = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "\"field" + i + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        for (String keyFields : new String[] {"[\"1code\"]", "[\"code\",\"code\"]", twentyOne}) {
            replace("ATTRIBUTE", sequenceWith(keyFields))
                    .andExpect(status().isBadRequest())
                    .andExpect(error("keyFields", "INVALID_KEY_FIELD_NAME"));
        }
    }

    @Test
    void requiresTheSequenceAndTheKeyFields() throws Exception {
        replace("ATTRIBUTE", "{\"keyFields\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(error("sequence", "REQUIRED"));
        replace("ATTRIBUTE", "{\"sequence\":{\"kind\":\"NUMERIC\",\"minimumDigits\":6,\"nextValue\":1}}")
                .andExpect(status().isBadRequest())
                .andExpect(error("keyFields", "REQUIRED"));
    }

    @Test
    void storesTheDeclaredKeyFields() throws Exception {
        replace("ATTRIBUTE", sequenceWith("[\"code\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyFields").value(contains("code")));
    }

    private static String sequenceWith(String keyFields) {
        return "{\"sequence\":{\"kind\":\"ALPHANUMERIC\",\"prefix\":\"ATR\",\"minimumDigits\":6,\"nextValue\":3},"
                + "\"keyFields\":" + keyFields + "}";
    }

    private ResultActions replace(String objectType, String body) throws Exception {
        return mockMvc.perform(put(OBJECT_TYPES + "/" + objectType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ResultMatcher error(String field, String code) {
        return result -> {
            jsonPath("$.errors[0].field").value(field).match(result);
            jsonPath("$.errors[0].code").value(code).match(result);
            jsonPath("$.errors[0].message", notNullValue()).match(result);
        };
    }
}
