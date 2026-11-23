package com.pim.backend.lov.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Bulk loads of several lists over HTTP (feature 013 contract lov-bulk-load.openapi.yaml; FR-010–FR-015). */
@SpringBootTest
@AutoConfigureMockMvc
class ListOfValuesBulkLoadControllerIntegrationTest {

    private static final String LISTS = "/api/v1/lists-of-values";
    private static final String CHECK = LISTS + "/bulk-load/check";
    private static final String APPLY = LISTS + "/bulk-load";
    private static final String CSV = "Lista,ID,Etiqueta,Equivalencia\n"
            + "Color,AZU,Azul marino,\n"
            + "Talla,0001,Chica,S\n"
            + "talla,0002,Mediana,M\n";

    private final MockMvc mockMvc;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    ListOfValuesBulkLoadControllerIntegrationTest(
            @Autowired MockMvc mockMvc,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.mockMvc = mockMvc;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    @BeforeEach
    void startWithColor() throws Exception {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
        mockMvc.perform(post(LISTS)
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Color\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(LISTS + "/LOV000001/values")
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"AZU\",\"label\":\"Azul\"}"))
                .andExpect(status().isCreated());
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "listas", "application/octet-stream", content);
    }

    private ResultActions check(byte[] content) throws Exception {
        return mockMvc.perform(multipart(CHECK).file(file(content)).header("X-Pim-Permissions", "ATTRIBUTE_CREATE"));
    }

    private ResultActions apply(byte[] content, String token) throws Exception {
        MockMultipartHttpServletRequestBuilder request =
                multipart(APPLY).file(file(content)).header("X-Pim-Permissions", "ATTRIBUTE_CREATE");
        if (token != null) {
            request.param("checkToken", token);
        }
        return mockMvc.perform(request.header("X-Pim-User", "ana"));
    }

    private String tokenOf(byte[] content) throws Exception {
        String body = check(content)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.checkToken");
    }

    private static byte[] excel() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Listas");
            String[][] rows = {
                {"Lista", "ID", "Etiqueta", "Equivalencia"},
                {"Color", "AZU", "Azul marino", ""},
                {"Talla", "0001", "Chica", "S"},
                {"talla", "0002", "Mediana", "M"}
            };
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index);
                for (int column = 0; column < rows[index].length; column++) {
                    row.createCell(column).setCellValue(rows[index][column]);
                }
            }
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }

    @Test
    void checksACsvFileWithoutSavingAnything() throws Exception {
        check(CSV.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.listsToCreate").value(1))
                .andExpect(jsonPath("$.listsToUpdate").value(1))
                .andExpect(jsonPath("$.valuesToAdd").value(2))
                .andExpect(jsonPath("$.valuesToUpdate").value(1))
                .andExpect(jsonPath("$.lists[0].name").value("Color"))
                .andExpect(jsonPath("$.lists[0].systemId").value("LOV000001"))
                .andExpect(jsonPath("$.lists[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.lists[1].name").value("Talla"))
                .andExpect(jsonPath("$.lists[1].systemId").isEmpty())
                .andExpect(jsonPath("$.lists[1].action").value("CREATE"))
                .andExpect(jsonPath("$.checkToken").isString());
        mockMvc.perform(get(LISTS)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void appliesAnExcelFileWithItsToken() throws Exception {
        byte[] workbook = excel();
        String token = tokenOf(workbook);

        apply(workbook, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listsCreated").value(1))
                .andExpect(jsonPath("$.listsUpdated").value(1))
                .andExpect(jsonPath("$.valuesAdded").value(2))
                .andExpect(jsonPath("$.valuesUpdated").value(1))
                .andExpect(jsonPath("$.lists[1].systemId").value("LOV000002"));
        mockMvc.perform(get(LISTS + "/LOV000002"))
                .andExpect(jsonPath("$.name").value("Talla"))
                .andExpect(jsonPath("$.values[0].id").value("0001"))
                .andExpect(jsonPath("$.values[1].equivalence").value("M"));
        mockMvc.perform(get(LISTS + "/LOV000001"))
                .andExpect(jsonPath("$.values[0].label").value("Azul marino"));
    }

    @Test
    void reportsEveryWrongRowAndSavesNothing() throws Exception {
        byte[] wrong =
                "Lista,ID,Etiqueta\nColor,ROJ,\nTalla,CH,Chica\ncolor,roj,Rojo\n".getBytes(StandardCharsets.UTF_8);

        check(wrong)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_IMPORT"))
                .andExpect(jsonPath("$.rowErrors.length()").value(3))
                .andExpect(jsonPath("$.rowErrors[0].row").value(2))
                .andExpect(jsonPath("$.rowErrors[0].column").value("ID"))
                .andExpect(jsonPath("$.rowErrors[0].message")
                        .value("El ID \"ROJ\" de la lista «Color» está repetido en el archivo (filas 2 y 4)."))
                .andExpect(jsonPath("$.rowErrors[1].column").value("Etiqueta"))
                .andExpect(jsonPath("$.rowErrors[2].row").value(4));
        apply(wrong, "x").andExpect(status().isUnprocessableContent());
        mockMvc.perform(get(LISTS)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void refusesUnreadableFiles() throws Exception {
        byte[] xls = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        check(xls)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_IMPORT_FILE"));
        check("ID,Etiqueta\nA,B".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("No se pudo leer el archivo: falta la columna Lista. No se aplicó ningún valor."));
        check(new byte[6 * 1024 * 1024])
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("IMPORT_TOO_LARGE"));
    }

    @Test
    void needsTheFileTheTokenAndThePermission() throws Exception {
        mockMvc.perform(multipart(CHECK).header("X-Pim-Permissions", "ATTRIBUTE_CREATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        apply(CSV.getBytes(StandardCharsets.UTF_8), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("checkToken"))
                .andExpect(jsonPath("$.errors[0].code").value("CHECK_TOKEN_REQUIRED"));
        mockMvc.perform(multipart(CHECK).file(file(CSV.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ATTRIBUTE_CREATE_NOT_PERMITTED"));
    }

    @Test
    void refusesAnOutdatedToken() throws Exception {
        byte[] csv = CSV.getBytes(StandardCharsets.UTF_8);
        String token = tokenOf(csv);
        mockMvc.perform(post(LISTS + "/LOV000001/values")
                        .header("X-Pim-Permissions", "ATTRIBUTE_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"ROJ\",\"label\":\"Rojo\"}"))
                .andExpect(status().isCreated());

        apply(csv, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("BULK_LOAD_OUTDATED"))
                .andExpect(jsonPath("$.detail")
                        .value("Las listas cambiaron desde la revisión. Vuelve a revisar el archivo."));
        mockMvc.perform(get(LISTS)).andExpect(jsonPath("$.items.length()").value(1));
    }

    // --- User Story 3: templates ---

    @Test
    void servesBothTemplates() throws Exception {
        mockMvc.perform(get(APPLY + "/template?format=xlsx"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"plantilla-listas-de-valores.xlsx\""));
        mockMvc.perform(get(APPLY + "/template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"plantilla-listas-de-valores.csv\""));
        mockMvc.perform(get(APPLY + "/template?format=pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("format"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }
}
