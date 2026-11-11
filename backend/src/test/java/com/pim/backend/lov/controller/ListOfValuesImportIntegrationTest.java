package com.pim.backend.lov.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** CSV template and bulk loads over HTTP (FR-011, FR-016; contract import operations; SC-003). */
@SpringBootTest
@AutoConfigureMockMvc
class ListOfValuesImportIntegrationTest {

    private static final String LISTS = "/api/v1/lists-of-values";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    ListOfValuesImportIntegrationTest(
            @Autowired MockMvc mockMvc,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.mockMvc = mockMvc;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    @BeforeEach
    void startWithOneList() throws Exception {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
        mockMvc.perform(post(LISTS)
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Color\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void servesTheTemplate() throws Exception {
        byte[] body = mockMvc.perform(get(LISTS + "/import-template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"plantilla-valores.csv\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("﻿ID,Etiqueta,Equivalencia\r\n");
    }

    @Test
    void importsFiveHundredValuesThenUpdatesWithASemicolonFile() throws Exception {
        StringBuilder csv = new StringBuilder("ID,Etiqueta,Equivalencia\n");
        for (int number = 1; number <= 500; number++) {
            csv.append("%04d,Color %d,%n".formatted(number, number));
        }

        upload("LOV000001", csv.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(500))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.total").value(500));
        upload("LOV000001", "ID;Etiqueta;Equivalencia\n0001;AZUL;\n;Verde;VERDE\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.total").value(501));
        mockMvc.perform(get(LISTS + "/LOV000001"))
                .andExpect(jsonPath("$.values[0].label").value("AZUL"))
                .andExpect(jsonPath("$.values[500].id").value("0501"))
                .andExpect(jsonPath("$.values[500].equivalence").value("VERDE"));
    }

    @Test
    void aFileWithErrorsAppliesNothing() throws Exception {
        String before = detail();

        upload("lov000001", "ID,Etiqueta\n0001,Uno\n0002,Dos\n0002,Tres\n0004,\n")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Archivo con errores"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_IMPORT"))
                .andExpect(jsonPath("$.detail").value("El archivo tiene 3 errores. No se aplicó ningún valor."))
                .andExpect(jsonPath("$.rowErrors[0].row").value(3))
                .andExpect(jsonPath("$.rowErrors[0].column").value("ID"))
                .andExpect(jsonPath("$.rowErrors[0].code").value("DUPLICATE_ID_IN_FILE"))
                .andExpect(jsonPath("$.rowErrors[1].row").value(4))
                .andExpect(jsonPath("$.rowErrors[2].row").value(5))
                .andExpect(jsonPath("$.rowErrors[2].column").value("Etiqueta"))
                .andExpect(jsonPath("$.rowErrors[2].code").value("REQUIRED"))
                .andExpect(jsonPath("$.rowErrors[2].message").value("Este dato es obligatorio."));

        assertThat(detail()).isEqualTo(before);
    }

    @Test
    void refusesUnreadableTooLargeOrMisdirectedUploads() throws Exception {
        upload("LOV000001", "ID,Nombre\n1,Azul\n")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_IMPORT_FILE"));
        byte[] large = new byte[(int) ListOfValuesImportController.MAX_FILE_BYTES + 1];
        mockMvc.perform(multipart(LISTS + "/LOV000001/values/import")
                        .file(new MockMultipartFile("file", "big.csv", "text/csv", large))
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("IMPORT_TOO_LARGE"));
        upload("LOV999999", "Etiqueta\nA\n").andExpect(status().isNotFound());
        mockMvc.perform(multipart(LISTS + "/LOV000001/values/import")
                        .file(new MockMultipartFile(
                                "file", "a.csv", "text/csv", "Etiqueta\nA\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(LISTS + "/LOV000001/values/import").header("X-Pim-Permissions", "ATTRIBUTE_CREATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void importsTenThousandValuesQuickly() throws Exception {
        StringBuilder csv = new StringBuilder("Etiqueta\n");
        csv.append("Valor\n".repeat(10_000));
        Instant start = Instant.now();

        upload("LOV000001", csv.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(10_000));

        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));
    }

    private String detail() throws Exception {
        return mockMvc.perform(get(LISTS + "/LOV000001"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ResultActions upload(String listId, String csv) throws Exception {
        return mockMvc.perform(multipart(LISTS + "/" + listId + "/values/import")
                .file(new MockMultipartFile("file", "valores.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                .header("X-Pim-User", "ana"));
    }
}
