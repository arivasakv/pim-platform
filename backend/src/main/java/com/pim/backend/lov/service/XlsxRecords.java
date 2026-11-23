package com.pim.backend.lov.service;

import com.pim.backend.lov.model.InvalidImportFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Reads the first sheet of an Excel (.xlsx) file as records of cell texts (feature 013 research R-01, R-03), with
 * Apache POI's streaming reader, so a large sheet is never held in memory as a workbook.
 *
 * <ul>
 *   <li>cell texts are what the user sees: a number formatted {@code 0000} reads {@code 0001}; a formula reads its
 *       last calculated value;
 *   <li>row numbers are those of the sheet (the first row is 1); rows with no cell are not returned, and empty cells
 *       before the last filled one are returned as {@code ""};
 *   <li>at most {@value CsvValuesParser#MAX_DATA_ROWS} non-blank rows after the first non-blank one.
 * </ul>
 */
final class XlsxRecords {

    private static final String UNREADABLE = "no es un archivo Excel (.xlsx) válido";

    private XlsxRecords() {}

    /**
     * The first sheet's records.
     *
     * @throws InvalidImportFileException {@code INVALID_IMPORT_FILE} for a file that is not a readable .xlsx
     *     workbook; {@code IMPORT_TOO_LARGE} for more than {@value CsvValuesParser#MAX_DATA_ROWS} data rows
     */
    static List<SheetRecord> read(byte[] content) {
        try (OPCPackage workbook = OPCPackage.open(new ByteArrayInputStream(content))) {
            XSSFReader reader = new XSSFReader(workbook);
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                return List.of();
            }
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(workbook);
            StylesTable styles = reader.getStylesTable();
            Rows rows = new Rows();
            try (InputStream sheet = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(
                        new XSSFSheetXMLHandler(styles, null, strings, rows, new DataFormatter(Locale.US), false));
                parser.parse(new InputSource(sheet));
            }
            return rows.records;
        } catch (IOException
                | OpenXML4JException
                | OpenXML4JRuntimeException
                | SAXException
                | ParserConfigurationException
                | IllegalArgumentException
                | IllegalStateException exception) {
            throw InvalidImportFileException.unreadable(UNREADABLE);
        }
    }

    /** Collects the rows of the sheet as POI streams them. */
    private static final class Rows implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final List<SheetRecord> records = new ArrayList<>();
        private List<String> cells = new ArrayList<>();
        private boolean headerSeen;
        private int dataRows;

        @Override
        public void startRow(int rowNum) {
            cells = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            SheetRecord record = new SheetRecord(rowNum + 1, cells);
            if (!record.isBlank()) {
                if (headerSeen && ++dataRows > CsvValuesParser.MAX_DATA_ROWS) {
                    throw InvalidImportFileException.tooLarge();
                }
                headerSeen = true;
            }
            records.add(record);
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference == null ? cells.size() : new CellReference(cellReference).getCol();
            while (cells.size() < column) {
                cells.add("");
            }
            cells.add(formattedValue == null ? "" : formattedValue);
        }
    }
}
