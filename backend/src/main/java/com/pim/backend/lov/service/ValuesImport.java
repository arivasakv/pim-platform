package com.pim.backend.lov.service;

import com.pim.backend.lov.model.GeneratedValueIds;
import com.pim.backend.lov.model.ImportOutcome;
import com.pim.backend.lov.model.ImportRowError;
import com.pim.backend.lov.model.InvalidImportException;
import com.pim.backend.lov.model.InvalidListException;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.LovErrorCode;
import com.pim.backend.lov.model.ValueId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the rows of a CSV file to a list, all or nothing (FR-011, FR-016; clarifications of 2026-11-11 on OQ-005;
 * data-model "CSV import"):
 *
 * <ul>
 *   <li>empty {@code ID}: a new value with a generated ID;
 *   <li>{@code ID} already in the list (ignoring case): its label is replaced, and its equivalence when the cell is
 *       not empty;
 *   <li>new {@code ID}: a new value at the end, in file order.
 * </ul>
 *
 * <p>Every row is checked first; if any is wrong, {@link InvalidImportException} lists them all and nothing is
 * returned to store. Pure: no storage, no clock.
 */
public final class ValuesImport {

    private static final Map<String, String> COLUMN_OF_FIELD = Map.of(
            "id", CsvValuesParser.ID_COLUMN,
            "label", CsvValuesParser.LABEL_COLUMN,
            "equivalence", CsvValuesParser.EQUIVALENCE_COLUMN);
    private static final List<String> COLUMN_ORDER =
            List.of(CsvValuesParser.ID_COLUMN, CsvValuesParser.LABEL_COLUMN, CsvValuesParser.EQUIVALENCE_COLUMN);

    private ValuesImport() {}

    /** One row after its cells were checked. */
    private record CheckedRow(Optional<ValueId> id, String label, Optional<String> equivalence) {}

    /**
     * The list with the rows applied.
     *
     * @throws InvalidImportException listing every wrong row; nothing is applied
     * @throws com.pim.backend.lov.model.ListTooLargeException if the list would exceed 50,000 values
     */
    public static ImportOutcome apply(ListOfValues list, List<CsvValuesParser.Row> rows, Instant now) {
        return apply(list, rows, now, null);
    }

    /**
     * The list with the rows applied; a duplicate ID is reported naming {@code listName} when it is not {@code null}
     * (feature 013, a file with several lists).
     *
     * @throws InvalidImportException listing every wrong row; nothing is applied
     * @throws com.pim.backend.lov.model.ListTooLargeException if the list would exceed 50,000 values
     */
    public static ImportOutcome apply(ListOfValues list, List<CsvValuesParser.Row> rows, Instant now, String listName) {
        List<ImportRowError> errors = new ArrayList<>();
        List<CheckedRow> checked = new ArrayList<>();
        Map<String, List<Integer>> rowsById = new LinkedHashMap<>();
        for (CsvValuesParser.Row row : rows) {
            CheckedRow checkedRow = check(row, errors);
            checked.add(checkedRow);
            // A valid ID counts for duplicates even when another cell of its row is wrong, so every problem is
            // reported at once (feature 013 FR-012).
            validId(row)
                    .ifPresent(id -> rowsById.computeIfAbsent(id.key(), key -> new ArrayList<>())
                            .add(row.number()));
        }
        rowsById.values().stream().filter(numbers -> numbers.size() > 1).forEach(numbers -> {
            for (int number : numbers) {
                String id = rows.stream()
                        .filter(row -> row.number() == number)
                        .findFirst()
                        .orElseThrow()
                        .id()
                        .strip();
                errors.add(new ImportRowError(
                        number,
                        CsvValuesParser.ID_COLUMN,
                        LovErrorCode.DUPLICATE_ID_IN_FILE,
                        listName == null
                                ? LovErrorCode.DUPLICATE_ID_IN_FILE.message(id, joined(numbers))
                                : LovErrorCode.DUPLICATE_ID_IN_LIST_OF_FILE.message(id, listName, joined(numbers))));
            }
        });
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(ImportRowError::row)
                    .thenComparingInt(error -> COLUMN_ORDER.indexOf(error.column())));
            throw new InvalidImportException(errors);
        }
        return applyChecked(list, checked, rowsById.keySet(), now);
    }

    private static ImportOutcome applyChecked(
            ListOfValues list, List<CheckedRow> rows, Set<String> idsInFile, Instant now) {
        List<ListValue> values = new ArrayList<>(list.values());
        Map<String, Integer> positionById = new HashMap<>();
        for (int position = 0; position < values.size(); position++) {
            positionById.put(values.get(position).id().key(), position);
        }
        Set<String> taken = list.idKeys();
        taken.addAll(idsInFile);
        long counter = list.nextGeneratedNumber();
        int added = 0;
        int updated = 0;
        for (CheckedRow row : rows) {
            Integer existing = row.id().map(id -> positionById.get(id.key())).orElse(null);
            if (existing != null) {
                ListValue current = values.get(existing);
                values.set(
                        existing,
                        new ListValue(
                                current.id(), row.label(), row.equivalence().or(current::equivalence)));
                updated++;
                continue;
            }
            ValueId id;
            if (row.id().isPresent()) {
                id = row.id().get();
            } else {
                long number = GeneratedValueIds.firstFree(counter, taken);
                id = ValueId.of(GeneratedValueIds.format(number));
                taken.add(id.key());
                counter = number + 1;
            }
            values.add(new ListValue(id, row.label(), row.equivalence()));
            added++;
        }
        return new ImportOutcome(list.withValues(values, counter, now), added, updated);
    }

    /** The row's ID when it is given and valid. */
    private static Optional<ValueId> validId(CsvValuesParser.Row row) {
        if (row.id() == null || row.id().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ValueId.of(row.id()));
        } catch (InvalidListException exception) {
            return Optional.empty();
        }
    }

    /** The row's cells checked one by one; {@code null} if any is wrong (its errors are added). */
    private static CheckedRow check(CsvValuesParser.Row row, List<ImportRowError> errors) {
        int before = errors.size();
        Optional<ValueId> id = Optional.empty();
        if (row.id() != null && !row.id().isBlank()) {
            try {
                id = Optional.of(ValueId.of(row.id()));
            } catch (InvalidListException exception) {
                errors.add(rowError(row, exception));
            }
        }
        String label = null;
        try {
            label = ListValue.label(row.label() == null || row.label().isBlank() ? null : row.label());
        } catch (InvalidListException exception) {
            errors.add(rowError(row, exception));
        }
        Optional<String> equivalence = Optional.empty();
        try {
            equivalence = ListValue.equivalence(row.equivalence());
        } catch (InvalidListException exception) {
            errors.add(rowError(row, exception));
        }
        return errors.size() == before ? new CheckedRow(id, label, equivalence) : null;
    }

    private static ImportRowError rowError(CsvValuesParser.Row row, InvalidListException exception) {
        return new ImportRowError(
                row.number(), COLUMN_OF_FIELD.get(exception.field()), exception.code(), exception.getMessage());
    }

    /** "4 y 9", "2, 5 y 7". */
    private static String joined(List<Integer> numbers) {
        List<String> texts = numbers.stream().map(String::valueOf).toList();
        if (texts.size() == 1) {
            return texts.get(0);
        }
        return texts.subList(0, texts.size() - 1).stream().collect(Collectors.joining(", "))
                + " y "
                + texts.get(texts.size() - 1);
    }
}
