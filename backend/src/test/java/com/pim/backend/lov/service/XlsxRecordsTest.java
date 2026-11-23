package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.lov.model.InvalidImportFileException;
import com.pim.backend.lov.model.LovErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Reading the first sheet of an Excel file as the user sees it (feature 013 research R-01, R-03). */
class XlsxRecordsTest {

    /** An .xlsx file built with POI, as Excel would save it. */
    static byte[] workbook(Consumer<XSSFWorkbook> content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            content.accept(workbook);
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** One sheet whose rows hold the given texts; a {@code null} row stays empty. */
    static byte[] textRows(String[]... rows) {
        return workbook(workbook -> {
            Sheet sheet = workbook.createSheet("Hoja1");
            for (int index = 0; index < rows.length; index++) {
                if (rows[index] == null) {
                    continue;
                }
                Row row = sheet.createRow(index);
                for (int column = 0; column < rows[index].length; column++) {
                    if (rows[index][column] != null) {
                        row.createCell(column).setCellValue(rows[index][column]);
                    }
                }
            }
        });
    }

    @Test
    void readsTheTextsOfTheFirstSheetWithTheRowNumbersTheUserSees() {
        byte[] file = textRows(
                new String[] {"Lista", "ID", "Etiqueta"}, new String[] {"Color", "ROJ", "Rojo"}, null, new String[] {
                    "Talla", null, "Chica"
                });

        List<SheetRecord> records = XlsxRecords.read(file);

        assertThat(records).extracting(SheetRecord::number).containsExactly(1, 2, 4);
        assertThat(records.get(0).cells()).containsExactly("Lista", "ID", "Etiqueta");
        assertThat(records.get(1).cells()).containsExactly("Color", "ROJ", "Rojo");
        assertThat(records.get(2).cells()).containsExactly("Talla", "", "Chica");
    }

    @Test
    void readsNumbersAndFormulasAsTheyAreShown() {
        byte[] file = workbook(workbook -> {
            Sheet sheet = workbook.createSheet("Hoja1");
            CellStyle fourDigits = workbook.createCellStyle();
            fourDigits.setDataFormat(workbook.createDataFormat().getFormat("0000"));
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            Row padded = sheet.createRow(1);
            padded.createCell(0).setCellValue(1);
            padded.getCell(0).setCellStyle(fourDigits);
            sheet.createRow(2).createCell(0).setCellValue(12);
            sheet.createRow(3).createCell(0).setCellValue(2.5);
            sheet.createRow(4).createCell(0).setCellFormula("\"A\"&\"Z\"");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
        });

        assertThat(XlsxRecords.read(file))
                .extracting(record -> record.cells().get(0))
                .containsExactly("ID", "0001", "12", "2.5", "AZ");
    }

    @Test
    void readsOnlyTheFirstSheet() {
        byte[] file = workbook(workbook -> {
            workbook.createSheet("Primera").createRow(0).createCell(0).setCellValue("Lista");
            workbook.createSheet("Segunda").createRow(0).createCell(0).setCellValue("Otra");
        });

        assertThat(XlsxRecords.read(file))
                .singleElement()
                .extracting(SheetRecord::cells)
                .isEqualTo(List.of("Lista"));
    }

    @Test
    void readsAnEmptySheetAsNoRecords() {
        assertThat(XlsxRecords.read(workbook(workbook -> workbook.createSheet("Hoja1"))))
                .isEmpty();
    }

    @Test
    void refusesAFileThatIsNotAnExcelWorkbook() {
        assertThatThrownBy(() -> XlsxRecords.read(new byte[] {'P', 'K', 3, 4, 1, 2, 3}))
                .isInstanceOfSatisfying(
                        InvalidImportFileException.class,
                        exception -> assertThat(exception.code()).isEqualTo(LovErrorCode.INVALID_IMPORT_FILE));
    }

    @Test
    void stopsPastTheRowLimit() {
        String[][] rows = new String[CsvValuesParser.MAX_DATA_ROWS + 2][];
        for (int index = 0; index < rows.length; index++) {
            rows[index] = new String[] {"x"};
        }

        assertThatThrownBy(() -> XlsxRecords.read(textRows(rows)))
                .isInstanceOfSatisfying(
                        InvalidImportFileException.class,
                        exception -> assertThat(exception.code()).isEqualTo(LovErrorCode.IMPORT_TOO_LARGE));
    }
}
