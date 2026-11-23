package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.lov.model.InvalidImportFileException;
import com.pim.backend.lov.model.LovErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CSV reading of bulk loads (FR-011; research R-09). */
class CsvValuesParserTest {

    @Test
    void readsACommaSeparatedFileWithBom() {
        List<CsvValuesParser.Row> rows = parse("﻿ID,Etiqueta,Equivalencia\r\n0001,Azul,BLUE\r\n0002,Rojo,\r\n");

        assertThat(rows)
                .containsExactly(
                        new CsvValuesParser.Row(2, "0001", "Azul", "BLUE"),
                        new CsvValuesParser.Row(3, "0002", "Rojo", ""));
    }

    @Test
    void readsASemicolonSeparatedFileWithoutBom() {
        assertThat(parse("ID;Etiqueta\n0001;Azul, rey\n"))
                .containsExactly(new CsvValuesParser.Row(2, "0001", "Azul, rey", null));
    }

    @Test
    void acceptsHeadersInAnyOrderIgnoringCaseAccentsAndSpaces() {
        assertThat(parse(" ETIQUETA , équivalencia ,id\nAzul,BLUE,1\n"))
                .containsExactly(new CsvValuesParser.Row(2, "1", "Azul", "BLUE"));
        assertThat(parse("etiqueta\nAzul\n")).containsExactly(new CsvValuesParser.Row(2, null, "Azul", null));
    }

    @Test
    void readsQuotedCells() {
        assertThat(parse("ID,Etiqueta\n1,\"a, b\"\n2,\"say \"\"hi\"\"\"\n3,\"two\nlines\"\n"))
                .extracting(CsvValuesParser.Row::label)
                .containsExactly("a, b", "say \"hi\"", "two\nlines");
    }

    @Test
    void ignoresEmptyLinesAndKeepsFileRowNumbers() {
        assertThat(parse("ID,Etiqueta\n\n1,Uno\n , \n2,Dos"))
                .extracting(CsvValuesParser.Row::number)
                .containsExactly(3, 5);
    }

    @Test
    void missingCellsAreEmpty() {
        assertThat(parse("ID,Etiqueta,Equivalencia\n1\n")).containsExactly(new CsvValuesParser.Row(2, "1", "", ""));
    }

    @Test
    void refusesWrongHeaders() {
        assertUnreadable(
                "ID,Nombre\n1,Azul\n",
                "No se pudo leer el archivo: la columna «Nombre» no es válida;"
                        + " usa ID, Etiqueta y Equivalencia. No se aplicó ningún valor.");
        assertUnreadable(
                "ID,Equivalencia\n1,X\n",
                "No se pudo leer el archivo: falta la columna Etiqueta." + " No se aplicó ningún valor.");
        assertUnreadable(
                "Etiqueta,etiqueta\nA,B\n",
                "No se pudo leer el archivo: la columna Etiqueta está" + " repetida. No se aplicó ningún valor.");
    }

    @Test
    void refusesEmptyFilesUnclosedQuotesExtraCellsAndOtherEncodings() {
        assertUnreadable("", "No se pudo leer el archivo: el archivo está vacío. No se aplicó ningún valor.");
        assertUnreadable(
                "ID,Etiqueta\n1,\"Azul\n",
                "No se pudo leer el archivo: hay comillas sin cerrar." + " No se aplicó ningún valor.");
        assertUnreadable(
                "ID,Etiqueta\n1,Azul,extra\n",
                "No se pudo leer el archivo: la fila 2 tiene más columnas"
                        + " que el encabezado. No se aplicó ningún valor.");
        byte[] latin1 = "ID,Etiqueta\n1,Café\n".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> CsvValuesParser.parse(latin1))
                .isInstanceOfSatisfying(
                        InvalidImportFileException.class,
                        exception -> assertThat(exception.code()).isEqualTo(LovErrorCode.INVALID_IMPORT_FILE));
    }

    @Test
    void refusesMoreThanFiftyThousandDataRows() {
        StringBuilder text = new StringBuilder("Etiqueta\n");
        text.append("x\n".repeat(CsvValuesParser.MAX_DATA_ROWS));
        assertThat(parse(text.toString())).hasSize(CsvValuesParser.MAX_DATA_ROWS);

        text.append("x\n");
        assertThatThrownBy(() -> parse(text.toString()))
                .isInstanceOfSatisfying(
                        InvalidImportFileException.class,
                        exception -> assertThat(exception.code()).isEqualTo(LovErrorCode.IMPORT_TOO_LARGE));
    }

    private static List<CsvValuesParser.Row> parse(String text) {
        return CsvValuesParser.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertUnreadable(String text, String message) {
        assertThatThrownBy(() -> parse(text)).isInstanceOfSatisfying(InvalidImportFileException.class, exception -> {
            assertThat(exception.code()).isEqualTo(LovErrorCode.INVALID_IMPORT_FILE);
            assertThat(exception.getMessage()).isEqualTo(message);
        });
    }
}
