package com.pim.backend.attribute.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /api/v1/attribute-types} (FR-007, FR-022), following the feature 004 contract. */
@SpringBootTest
@AutoConfigureMockMvc
class AttributeTypeControllerIntegrationTest {

    private final MockMvc mockMvc;

    AttributeTypeControllerIntegrationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void listsTheSevenTypesInOrderWithSpanishLabelsAndOptions() throws Exception {
        mockMvc.perform(get("/api/v1/attribute-types"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.items[*].code")
                        .value(contains(
                                "TEXT", "NUMERIC", "BOOLEAN", "LIST_OF_VALUES", "DATE_TIME", "CURRENCY", "REFERENCE")))
                .andExpect(jsonPath("$.items[*].label")
                        .value(contains(
                                "Texto (String)",
                                "Numérico (Integer / Float)",
                                "Booleano (Sí/No)",
                                "Lista de Valores (LOV)",
                                "Fecha y Hora (Date/Time)",
                                "Moneda (Currency)",
                                "Referencias / Relaciones")))
                .andExpect(jsonPath("$.items[0].options.caseFormat", hasSize(3)))
                .andExpect(jsonPath("$.items[0].options.caseFormat[0].code").value("UPPERCASE"))
                .andExpect(jsonPath("$.items[0].options.caseFormat[0].label").value("Mayúsculas"))
                .andExpect(jsonPath("$.items[1].options.numberFormat", hasSize(2)))
                .andExpect(jsonPath("$.items[2].options").isEmpty())
                .andExpect(jsonPath("$.items[3].options.selectionMode", hasSize(2)))
                .andExpect(jsonPath("$.items[4].options.dateFormat[*].code")
                        .value(contains(
                                "DD/MM/AAAA", "MM/DD/AAAA", "AAAA-MM-DD", "DD/MM/AAAA HH:mm", "AAAA-MM-DD HH:mm")))
                .andExpect(jsonPath("$.items[5].options.currencyCode[0].code").value("MXN"))
                .andExpect(jsonPath("$.items[5].options.currencyCode[0].label").value("Peso mexicano"))
                .andExpect(jsonPath("$.items[5].options.currencyCode[0].symbol").value("$"))
                .andExpect(jsonPath("$.items[5].options.currencyCode[1].code").value("USD"))
                .andExpect(jsonPath("$.items[5].options.currencyCode[2].code").value("EUR"))
                .andExpect(jsonPath("$.items[5].options.currencyCode[2].symbol").value("€"))
                .andExpect(jsonPath("$.items[5].options.currencyCode.length()", greaterThan(100)))
                .andExpect(jsonPath("$.items[0].options.caseFormat[0].symbol").doesNotExist())
                .andExpect(jsonPath("$.items[6].options.targetObjectType", hasSize(6)))
                .andExpect(jsonPath("$.items[6].options.cardinality", hasSize(2)));
    }
}
