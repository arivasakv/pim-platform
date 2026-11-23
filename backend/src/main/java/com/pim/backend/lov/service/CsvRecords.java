package com.pim.backend.lov.service;

import com.pim.backend.lov.model.InvalidImportFileException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a CSV file into records of cell texts (feature 006 research R-09; feature 013 research R-02), without a
 * library:
 *
 * <ul>
 *   <li>UTF-8, with or without BOM ("CSV UTF-8" from Excel);
 *   <li>comma or semicolon, whichever appears more often in the first line;
 *   <li>RFC 4180 quotes ({@code "a, b"}, {@code ""} inside quotes).
 * </ul>
 *
 * <p>Every record is returned, blank ones included, numbered from 1 in file order, so the header is record 1. Shared
 * by the per-list value import ({@link CsvValuesParser}) and the bulk load of lists ({@link BulkLoadFileReader}).
 */
final class CsvRecords {

    private static final char BOM = 0xFEFF;

    private CsvRecords() {}

    /**
     * The file's records.
     *
     * @throws InvalidImportFileException {@code INVALID_IMPORT_FILE} for a file that is not UTF-8 or has an unclosed
     *     quote
     */
    static List<SheetRecord> read(byte[] content) {
        String text = decode(content);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        List<List<String>> cells = records(text, delimiterOf(text));
        List<SheetRecord> records = new ArrayList<>(cells.size());
        for (int index = 0; index < cells.size(); index++) {
            records.add(new SheetRecord(index + 1, cells.get(index)));
        }
        return records;
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
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
}
