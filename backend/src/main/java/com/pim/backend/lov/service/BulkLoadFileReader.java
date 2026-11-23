package com.pim.backend.lov.service;

import com.pim.backend.lov.model.BulkLoadRow;
import com.pim.backend.lov.model.InvalidImportFileException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a bulk load file of lists of values (feature 013 FR-005; research R-02–R-04, R-09):
 *
 * <ul>
 *   <li>Excel (.xlsx) when the file starts with the zip signature, read by {@link XlsxRecords}; an old Excel file
 *       (.xls) is refused; anything else is CSV, read by {@link CsvRecords} with feature 006's rules;
 *   <li>the first non-blank row is the header: {@code Lista}, {@code ID} and {@code Etiqueta} required,
 *       {@code Equivalencia} optional, in any order, ignoring letter case, accents and surrounding spaces;
 *   <li>blank rows are skipped; at most {@value CsvValuesParser#MAX_DATA_ROWS} data rows.
 * </ul>
 *
 * <p>Cell texts are returned as written; their rules are checked by {@link BulkLoadPlan}.
 */
public final class BulkLoadFileReader {

    static final String LIST_COLUMN = "Lista";
    static final String ID_COLUMN = "ID";
    static final String LABEL_COLUMN = "Etiqueta";
    static final String EQUIVALENCE_COLUMN = "Equivalencia";

    private static final Map<String, String> COLUMNS =
            Map.of("lista", LIST_COLUMN, "id", ID_COLUMN, "etiqueta", LABEL_COLUMN, "equivalencia", EQUIVALENCE_COLUMN);
    private static final List<String> REQUIRED = List.of(LIST_COLUMN, ID_COLUMN, LABEL_COLUMN);

    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
    private static final byte[] OLE2_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private BulkLoadFileReader() {}

    /**
     * The data rows of a file, in file order.
     *
     * @throws InvalidImportFileException {@code INVALID_IMPORT_FILE} for an unreadable file, an old .xls file, a wrong
     *     header or no data rows; {@code IMPORT_TOO_LARGE} for more than {@value CsvValuesParser#MAX_DATA_ROWS} data
     *     rows
     */
    public static List<BulkLoadRow> read(byte[] content) {
        if (startsWith(content, OLE2_SIGNATURE)) {
            throw InvalidImportFileException.unreadable(
                    "es un archivo de Excel antiguo (.xls); guárdalo como Excel (.xlsx) o CSV");
        }
        List<SheetRecord> records =
                startsWith(content, ZIP_SIGNATURE) ? XlsxRecords.read(content) : CsvRecords.read(content);
        int headerIndex = 0;
        while (headerIndex < records.size() && records.get(headerIndex).isBlank()) {
            headerIndex++;
        }
        if (headerIndex == records.size()) {
            throw InvalidImportFileException.unreadable("el archivo está vacío");
        }
        Map<String, Integer> positions = header(records.get(headerIndex).cells());
        List<BulkLoadRow> rows = new ArrayList<>();
        for (SheetRecord record : records.subList(headerIndex + 1, records.size())) {
            if (record.isBlank()) {
                continue;
            }
            checkWidth(record, positions.size());
            if (rows.size() == CsvValuesParser.MAX_DATA_ROWS) {
                throw InvalidImportFileException.tooLarge();
            }
            List<String> cells = record.cells();
            rows.add(new BulkLoadRow(
                    record.number(),
                    cell(cells, positions.get(LIST_COLUMN)),
                    cell(cells, positions.get(ID_COLUMN)),
                    cell(cells, positions.get(LABEL_COLUMN)),
                    cell(cells, positions.get(EQUIVALENCE_COLUMN))));
        }
        if (rows.isEmpty()) {
            throw InvalidImportFileException.unreadable("el archivo no tiene filas de datos");
        }
        return rows;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Integer> header(List<String> cells) {
        Map<String, Integer> positions = new HashMap<>();
        for (int position = 0; position < cells.size(); position++) {
            String written = cells.get(position).strip();
            if (written.isEmpty()
                    && cells.subList(position, cells.size()).stream().allMatch(String::isBlank)) {
                break;
            }
            String column = COLUMNS.get(normalized(written));
            if (column == null) {
                throw InvalidImportFileException.unreadable(
                        "la columna «%s» no es válida; usa Lista, ID, Etiqueta y Equivalencia".formatted(written));
            }
            if (positions.put(column, position) != null) {
                throw InvalidImportFileException.unreadable("la columna %s está repetida".formatted(column));
            }
        }
        for (String column : REQUIRED) {
            if (!positions.containsKey(column)) {
                throw InvalidImportFileException.unreadable("falta la columna %s".formatted(column));
            }
        }
        return positions;
    }

    private static void checkWidth(SheetRecord record, int columns) {
        List<String> cells = record.cells();
        for (int position = columns; position < cells.size(); position++) {
            if (!cells.get(position).isBlank()) {
                throw InvalidImportFileException.unreadable(
                        "la fila %d tiene más columnas que el encabezado".formatted(record.number()));
            }
        }
    }

    private static String normalized(String header) {
        return Normalizer.normalize(header, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String cell(List<String> cells, Integer position) {
        if (position == null) {
            return null;
        }
        return position < cells.size() ? cells.get(position) : "";
    }
}
