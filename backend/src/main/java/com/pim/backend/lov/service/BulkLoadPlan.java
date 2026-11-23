package com.pim.backend.lov.service;

import com.pim.backend.lov.model.BulkLoadRow;
import com.pim.backend.lov.model.ImportOutcome;
import com.pim.backend.lov.model.ImportRowError;
import com.pim.backend.lov.model.InvalidImportException;
import com.pim.backend.lov.model.InvalidListException;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListTooLargeException;
import com.pim.backend.lov.model.LovErrorCode;
import com.pim.backend.lov.model.PlannedListChange;
import com.pim.backend.lov.model.PlannedListChange.Action;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a bulk load file does to the lists it names (feature 013 FR-006–FR-009, FR-012, FR-014; data-model "Bulk load
 * plan"). Pure: no storage, no system IDs.
 *
 * <ul>
 *   <li>rows are grouped by list name ignoring letter case and surrounding spaces, in order of first appearance;
 *   <li>an existing list gets feature 006's import rules ({@link ValuesImport});
 *   <li>a missing list is planned as a new list named as in its first row, with the equivalence column on when any of
 *       its rows has an equivalence (research R-08), and its rows added by the same rules;
 *   <li>every problem of every row is collected before failing; a list over 50,000 values fails the file.
 * </ul>
 */
public final class BulkLoadPlan {

    /** Placeholder ID of a list to create; replaced by the feature 003 ID when it is created (research R-07). */
    static final String NEW_LIST_ID = "";

    private static final List<String> COLUMN_ORDER = List.of(
            BulkLoadFileReader.LIST_COLUMN,
            BulkLoadFileReader.ID_COLUMN,
            BulkLoadFileReader.LABEL_COLUMN,
            BulkLoadFileReader.EQUIVALENCE_COLUMN);

    /**
     * One list the file creates or changes.
     *
     * @param planned what changes, as shown in the summary
     * @param before the stored list; empty for a list to create
     * @param after the list with the file applied; a list to create has the placeholder ID {@value #NEW_LIST_ID}
     */
    public record ListChange(PlannedListChange planned, Optional<ListOfValues> before, ListOfValues after) {}

    private final List<ListChange> changes;

    private BulkLoadPlan(List<ListChange> changes) {
        this.changes = List.copyOf(changes);
    }

    /**
     * Plans the rows against the stored lists.
     *
     * @param existing the stored list with a name, compared ignoring letter case
     * @throws InvalidImportException listing every wrong row of every list
     * @throws ListTooLargeException naming the first list that would exceed 50,000 values
     */
    public static BulkLoadPlan of(
            List<BulkLoadRow> rows, Function<ListName, Optional<ListOfValues>> existing, Instant now) {
        List<ImportRowError> errors = new ArrayList<>();
        Map<String, List<BulkLoadRow>> rowsByList = new LinkedHashMap<>();
        Map<String, ListName> names = new LinkedHashMap<>();
        for (BulkLoadRow row : rows) {
            Optional<ListName> name = listName(row, errors);
            if (name.isPresent()) {
                String key = name.get().comparisonKey();
                names.putIfAbsent(key, name.get());
                rowsByList.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            } else {
                // The other cells of a row without a valid list are still checked, on their own.
                planList(ListOfValues.create(NEW_LIST_ID, ListName.of("-"), now), List.of(row), now, errors);
            }
        }
        List<ListChange> changes = new ArrayList<>();
        ListTooLargeException tooLarge = null;
        for (Map.Entry<String, List<BulkLoadRow>> group : rowsByList.entrySet()) {
            ListName name = names.get(group.getKey());
            List<BulkLoadRow> groupRows = group.getValue();
            Optional<ListOfValues> before = existing.apply(name);
            ListOfValues target = before.orElseGet(() -> ListOfValues.create(NEW_LIST_ID, name, now)
                    .withEquivalencesEnabled(hasEquivalence(groupRows), now));
            try {
                Optional<ImportOutcome> outcome = planList(target, groupRows, now, errors);
                outcome.ifPresent(applied -> changes.add(new ListChange(
                        new PlannedListChange(
                                target.name().value(),
                                before.map(ListOfValues::systemId),
                                before.isPresent() ? Action.UPDATE : Action.CREATE,
                                applied.added(),
                                applied.updated()),
                        before,
                        applied.list())));
            } catch (ListTooLargeException exception) {
                if (tooLarge == null) {
                    tooLarge = new ListTooLargeException(name.value(), sizeAfter(target, groupRows));
                }
            }
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(ImportRowError::row)
                    .thenComparingInt(error -> COLUMN_ORDER.indexOf(error.column())));
            throw new InvalidImportException(errors);
        }
        if (tooLarge != null) {
            throw tooLarge;
        }
        return new BulkLoadPlan(changes);
    }

    /** Every list the file creates or changes, in order of first appearance. */
    public List<ListChange> changes() {
        return changes;
    }

    /** What changes, as shown in the summary. */
    public List<PlannedListChange> planned() {
        return changes.stream().map(ListChange::planned).toList();
    }

    /**
     * Identifies the file and the state of the lists it touches (research R-05): SHA-256, in hexadecimal, of the file
     * bytes, every planned change and the full content of every stored list involved (name, equivalence setting, ID
     * counter and values), so any change to those lists since the check gives another token.
     */
    public String checkToken(byte[] file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file);
            for (ListChange change : changes) {
                PlannedListChange planned = change.planned();
                update(
                        digest,
                        change.after().name().comparisonKey(),
                        planned.action().name(),
                        Integer.toString(planned.valuesAdded()),
                        Integer.toString(planned.valuesUpdated()));
                change.before().ifPresent(list -> {
                    update(
                            digest,
                            list.systemId(),
                            list.name().value(),
                            Boolean.toString(list.equivalencesEnabled()),
                            Long.toString(list.nextGeneratedNumber()));
                    list.values()
                            .forEach(value -> update(
                                    digest,
                                    value.id().value(),
                                    value.label(),
                                    value.equivalence().orElse("")));
                });
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }

    /** Adds the parts to the digest, each followed by a separator that cannot appear in them. */
    private static void update(MessageDigest digest, String... parts) {
        for (String part : parts) {
            digest.update(part.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
    }

    private static Optional<ListName> listName(BulkLoadRow row, List<ImportRowError> errors) {
        if (row.list() == null || row.list().isBlank()) {
            errors.add(new ImportRowError(
                    row.number(),
                    BulkLoadFileReader.LIST_COLUMN,
                    LovErrorCode.REQUIRED,
                    LovErrorCode.REQUIRED.message()));
            return Optional.empty();
        }
        try {
            return Optional.of(ListName.of(row.list()));
        } catch (InvalidListException exception) {
            errors.add(new ImportRowError(
                    row.number(), BulkLoadFileReader.LIST_COLUMN, exception.code(), exception.getMessage()));
            return Optional.empty();
        }
    }

    /** The list with the rows applied, or empty when a row is wrong (its errors are added). */
    private static Optional<ImportOutcome> planList(
            ListOfValues list, List<BulkLoadRow> rows, Instant now, List<ImportRowError> errors) {
        List<CsvValuesParser.Row> valueRows = rows.stream()
                .map(row -> new CsvValuesParser.Row(row.number(), row.id(), row.label(), row.equivalence()))
                .toList();
        try {
            return Optional.of(
                    ValuesImport.apply(list, valueRows, now, list.name().value()));
        } catch (InvalidImportException exception) {
            errors.addAll(exception.rowErrors());
            return Optional.empty();
        }
    }

    private static boolean hasEquivalence(List<BulkLoadRow> rows) {
        return rows.stream()
                .anyMatch(row -> row.equivalence() != null && !row.equivalence().isBlank());
    }

    /** How many values the list would have: its own plus every row that is not one of its IDs. */
    private static int sizeAfter(ListOfValues list, List<BulkLoadRow> rows) {
        java.util.Set<String> keys = list.idKeys();
        int added = 0;
        for (BulkLoadRow row : rows) {
            if (row.id() == null || row.id().isBlank()) {
                added++;
            } else if (keys.add(com.pim.backend.lov.model.ValueId.keyOf(row.id()))) {
                added++;
            }
        }
        return list.values().size() + added;
    }
}
