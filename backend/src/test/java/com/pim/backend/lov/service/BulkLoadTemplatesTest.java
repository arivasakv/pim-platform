package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.lov.model.BulkLoadRow;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** The bulk load templates (feature 013 FR-003, research R-12). */
class BulkLoadTemplatesTest {

    private static final BulkLoadRow EXAMPLE = new BulkLoadRow(2, "Color", "ROJ", "Rojo", "RED");

    @Test
    void theCsvTemplateHasTheFourColumnsAndAnExampleRow() {
        byte[] csv = BulkLoadTemplates.csv();

        assertThat(new String(csv, StandardCharsets.UTF_8))
                .isEqualTo("﻿Lista,ID,Etiqueta,Equivalencia\r\nColor,ROJ,Rojo,RED\r\n");
        assertThat(BulkLoadFileReader.read(csv)).containsExactly(EXAMPLE);
    }

    @Test
    void theExcelTemplateReadsLikeTheCsvTemplateAndKeepsIdsAsText() throws IOException {
        byte[] excel = BulkLoadTemplates.excel();

        assertThat(BulkLoadFileReader.read(excel)).containsExactly(EXAMPLE);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Listas de valores");
            assertThat(workbook.getFontAt(
                                    sheet.getRow(0).getCell(0).getCellStyle().getFontIndex())
                            .getBold())
                    .isTrue();
            assertThat(sheet.getColumnStyle(1).getDataFormatString()).isEqualTo("@");
        }
        assertThat(BulkLoadTemplates.excel()).isSameAs(excel);
    }
}
