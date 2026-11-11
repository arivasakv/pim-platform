package com.pim.backend.lov.service;

import com.pim.backend.lov.model.InvalidImportFileException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the CSV files of bulk value loads (FR-011; research R-09), without a library (constitution II):
 *
 * <ul>
 *   <li>UTF-8, with or without BOM ("CSV UTF-8" from Excel);
 *   <li>comma or semicolon, whichever appears more often in the header line;
 *   <li>header with {@code Etiqueta} and optionally {@code ID} and {@code Equivalencia}, in any order, ignoring case,
 *       accents and surrounding spaces;
 *   <li>RFC 4180 quotes ({@code "a, b"}, {@code ""} inside quotes);
 *   <li>empty lines ignored; at most {@value #MAX_DATA_ROWS} data rows.
 * </ul>
 *
 * <p>Row numbers are those of the file, counting the header as row 1. Cell texts are returned as written; the rules
 * of IDs, labels and equivalences are checked by {@link ValuesImport}.
 */
public final class CsvValuesParser {

    /** Most data rows a file may have (research R-08). */
    public static final int MAX_DATA_ROWS = 50_000;

    static final String ID_COLUMN = "ID";
    static final String LABEL_COLUMN = "Etiqueta";
    static final String EQUIVALENCE_COLUMN = "Equivalencia";

    private static final Map<String, String> COLUMNS =
            Map.of("id", ID_COLUMN, "etiqueta", LABEL_COLUMN, "equivalencia", EQUIVALENCE_COLUMN);

    /** One data row: its number in the file and its three cells ({@code null} when the column is missing). */
    public record Row(int number, String id, String label, String equivalence) {}

    private CsvValuesParser() {}

    /**
     * The data rows of a file.
     *
     * @throws InvalidImportFileException {@code INVALID_IMPORT_FILE} for a file that is not UTF-8, is empty, has an
     *     unclosed quote or a wrong header; {@code IMPORT_TOO_LARGE} for more than {@value #MAX_DATA_ROWS} data rows
     */
    public static List<Row> parse(byte[] content) {
        String text = decode(content);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = delimiterOf(text);
        List<List<String>> records = records(text, delimiter);
        if (records.isEmpty() || isBlank(records.get(0))) {
            throw InvalidImportFileException.unreadable("el archivo está vacío");
        }
        Map<String, Integer> positions = header(records.get(0));
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> cells = records.get(index);
            if (isBlank(cells)) {
                continue;
            }
            int number = index + 1;
            checkWidth(cells, positions.size(), number);
            if (rows.size() == MAX_DATA_ROWS) {
                throw InvalidImportFileException.tooLarge();
            }
            rows.add(new Row(
                    number,
                    cell(cells, positions.get(ID_COLUMN)),
                    cell(cells, positions.get(LABEL_COLUMN)),
                    cell(cells, positions.get(EQUIVALENCE_COLUMN))));
        }
        return rows;
    }

    private static String decode(byte[] content) {
        try {
            CharBuffer chars = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return chars.toString();
        } catch (CharacterCodingException exception) {
            throw InvalidImportFileException.unreadable("el archivo no está guardado como CSV UTF-8");
        }
    }

    /** Comma or semicolon, whichever appears more often in the first line (comma when tied or absent). */
    private static char delimiterOf(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        long commas = firstLine.chars().filter(character -> character == ',').count();
        long semicolons =
                firstLine.chars().filter(character -> character == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    /** RFC 4180 records; a record that is one empty cell stands for an empty line. */
    private static List<List<String>> records(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else if (character == '"') {
                    quoted = false;
                } else {
                    cell.append(character);
                }
            } else if (character == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (character == delimiter) {
                current.add(cell.toString());
                cell.setLength(0);
            } else if (character == '\r' || character == '\n') {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                current.add(cell.toString());
                cell.setLength(0);
                records.add(current);
                current = new ArrayList<>();
            } else {
                cell.append(character);
            }
            index++;
        }
        if (quoted) {
            throw InvalidImportFileException.unreadable("hay comillas sin cerrar");
        }
        if (!cell.isEmpty() || !current.isEmpty()) {
            current.add(cell.toString());
            records.add(current);
        }
        return records;
    }

    private static Map<String, Integer> header(List<String> cells) {
        Map<String, Integer> positions = new HashMap<>();
        for (int position = 0; position < cells.size(); position++) {
            String written = cells.get(position).strip();
            String column = COLUMNS.get(normalized(written));
            if (column == null) {
                throw InvalidImportFileException.unreadable(
                        "la columna «%s» no es válida; usa ID, Etiqueta y Equivalencia".formatted(written));
            }
            if (positions.put(column, position) != null) {
                throw InvalidImportFileException.unreadable("la columna %s está repetida".formatted(column));
            }
        }
        if (!positions.containsKey(LABEL_COLUMN)) {
            throw InvalidImportFileException.unreadable("falta la columna Etiqueta");
        }
        return positions;
    }

    private static void checkWidth(List<String> cells, int columns, int number) {
        for (int position = columns; position < cells.size(); position++) {
            if (!cells.get(position).isBlank()) {
                throw InvalidImportFileException.unreadable(
                        "la fila %d tiene más columnas que el encabezado".formatted(number));
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

    private static boolean isBlank(List<String> cells) {
        return cells.stream().allMatch(String::isBlank);
    }
}
