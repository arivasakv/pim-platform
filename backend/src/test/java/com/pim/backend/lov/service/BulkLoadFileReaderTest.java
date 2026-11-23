package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.lov.model.BulkLoadRow;
import com.pim.backend.lov.model.InvalidImportFileException;
import com.pim.backend.lov.model.LovErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading bulk load files of lists of values (feature 013 FR-005, research R-02–R-04, R-09). */
class BulkLoadFileReaderTest {

    private static List<BulkLoadRow> csv(String text) {
        return BulkLoadFileReader.read(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertUnreadable(byte[] file, String messagePart) {
        assertThatThrownBy(() -> BulkLoadFileReader.read(file))
                .isInstanceOfSatisfying(InvalidImportFileException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(LovErrorCode.INVALID_IMPORT_FILE);
                    assertThat(exception.getMessage()).contains(messagePart);
                });
    }

    @Test
    void readsTheFourColumnsOfACsvFileInAnyOrder() {
        List<BulkLoadRow> rows = csv("Etiqueta,LISTA, Id ,Equivalencia\r\nRojo,Color,ROJ,RED\r\n\r\nChica,Talla,,\r\n");

        assertThat(rows)
                .containsExactly(
                        new BulkLoadRow(2, "Color", "ROJ", "Rojo", "RED"),
                        new BulkLoadRow(4, "Talla", "", "Chica", ""));
    }

    @Test
    void readsAnExcelFileLikeTheSameCsvFile() {
        byte[] excel = XlsxRecordsTest.textRows(
                new String[] {"Lista", "ID", "Etiqueta", "Equivalencia"},
                new String[] {"Color", "ROJ", "Rojo", "RED"},
                null,
                new String[] {"Talla", null, "Chica"});

        assertThat(BulkLoadFileReader.read(excel))
                .isEqualTo(csv("Lista,ID,Etiqueta,Equivalencia\nColor,ROJ,Rojo,RED\n\nTalla,,Chica,\n"));
    }

    @Test
    void acceptsAFileWithoutTheEquivalenceColumn() {
        assertThat(csv("Lista;ID;Etiqueta\nColor;ROJ;Rojo"))
                .containsExactly(new BulkLoadRow(2, "Color", "ROJ", "Rojo", null));
    }

    @Test
    void acceptsAccentsAndLetterCaseInTheHeader() {
        assertThat(csv("lísta,id,ETIQUETA,equivalencia\nColor,ROJ,Rojo,")).hasSize(1);
    }

    @Test
    void refusesMissingUnknownOrRepeatedColumns() {
        assertUnreadable("ID,Etiqueta\nROJ,Rojo".getBytes(StandardCharsets.UTF_8), "falta la columna Lista");
        assertUnreadable("Lista,Etiqueta\nColor,Rojo".getBytes(StandardCharsets.UTF_8), "falta la columna ID");
        assertUnreadable("Lista,ID\nColor,ROJ".getBytes(StandardCharsets.UTF_8), "falta la columna Etiqueta");
        assertUnreadable(
                "Lista,ID,Etiqueta,Nombre\nColor,ROJ,Rojo,x".getBytes(StandardCharsets.UTF_8),
                "la columna «Nombre» no es válida");
        assertUnreadable(
                "Lista,ID,Etiqueta,lista\nColor,ROJ,Rojo,x".getBytes(StandardCharsets.UTF_8),
                "la columna Lista está repetida");
    }

    @Test
    void refusesEmptyFilesFilesWithoutRowsAndWiderRows() {
        assertUnreadable(new byte[0], "el archivo está vacío");
        assertUnreadable("\n\n".getBytes(StandardCharsets.UTF_8), "el archivo está vacío");
        assertUnreadable("Lista,ID,Etiqueta\n\n".getBytes(StandardCharsets.UTF_8), "no tiene filas de datos");
        assertUnreadable(
                "Lista,ID,Etiqueta\nColor,ROJ,Rojo,extra".getBytes(StandardCharsets.UTF_8),
                "la fila 2 tiene más columnas que el encabezado");
        assertUnreadable(XlsxRecordsTest.textRows(), "el archivo está vacío");
    }

    @Test
    void refusesAnOldExcelFile() {
        byte[] xls = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0};

        assertUnreadable(xls, "guárdalo como Excel (.xlsx) o CSV");
    }

    @Test
    void refusesMoreThanFiftyThousandDataRows() {
        StringBuilder text = new StringBuilder("Lista,ID,Etiqueta\n");
        for (int row = 0; row <= CsvValuesParser.MAX_DATA_ROWS; row++) {
            text.append("Color,").append(row).append(",Valor\n");
        }

        assertThatThrownBy(() -> csv(text.toString()))
                .isInstanceOfSatisfying(
                        InvalidImportFileException.class,
                        exception -> assertThat(exception.code()).isEqualTo(LovErrorCode.IMPORT_TOO_LARGE));
    }
}
