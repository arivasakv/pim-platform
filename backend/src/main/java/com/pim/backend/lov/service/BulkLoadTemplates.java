package com.pim.backend.lov.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Templates of the bulk load of lists of values, in CSV and Excel (feature 013 FR-003, research R-12): the columns
 * Lista, ID, Etiqueta and Equivalencia and one example row. In Excel the header is bold and the ID column is formatted
 * as text, so IDs such as {@code 0001} keep their leading zeros.
 */
public final class BulkLoadTemplates {

    private static final List<String> HEADER = List.of(
            BulkLoadFileReader.LIST_COLUMN,
            BulkLoadFileReader.ID_COLUMN,
            BulkLoadFileReader.LABEL_COLUMN,
            BulkLoadFileReader.EQUIVALENCE_COLUMN);
    private static final List<String> EXAMPLE = List.of("Color", "ROJ", "Rojo", "RED");
    private static final int ID_COLUMN_INDEX = 1;
    private static final int COLUMN_WIDTH = 24 * 256;

    private BulkLoadTemplates() {}

    /** The CSV template: UTF-8 with BOM, so Excel opens it with accents intact. */
    public static byte[] csv() {
        String text = "﻿" + String.join(",", HEADER) + "\r\n" + String.join(",", EXAMPLE) + "\r\n";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The Excel template, built once. */
    public static byte[] excel() {
        return ExcelTemplate.BYTES;
    }

    private static final class ExcelTemplate {
        static final byte[] BYTES = build();

        private static byte[] build() {
            try (XSSFWorkbook workbook = new XSSFWorkbook();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Listas de valores");
                CellStyle text = workbook.createCellStyle();
                text.setDataFormat(workbook.createDataFormat().getFormat("@"));
                Font bold = workbook.createFont();
                bold.setBold(true);
                CellStyle header = workbook.createCellStyle();
                header.setFont(bold);
                for (int column = 0; column < HEADER.size(); column++) {
                    sheet.setColumnWidth(column, COLUMN_WIDTH);
                }
                sheet.setDefaultColumnStyle(ID_COLUMN_INDEX, text);
                write(sheet.createRow(0), HEADER, header);
                Row example = sheet.createRow(1);
                write(example, EXAMPLE, null);
                example.getCell(ID_COLUMN_INDEX).setCellStyle(text);
                workbook.write(bytes);
                return bytes.toByteArray();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static void write(Row row, List<String> texts, CellStyle style) {
            for (int column = 0; column < texts.size(); column++) {
                Cell cell = row.createCell(column);
                cell.setCellValue(texts.get(column));
                if (style != null) {
                    cell.setCellStyle(style);
                }
            }
        }
    }
}
