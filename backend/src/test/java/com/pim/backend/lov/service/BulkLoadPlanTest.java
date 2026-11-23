package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.lov.model.BulkLoadRow;
import com.pim.backend.lov.model.ImportRowError;
import com.pim.backend.lov.model.InvalidImportException;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListTooLargeException;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.LovErrorCode;
import com.pim.backend.lov.model.PlannedListChange;
import com.pim.backend.lov.model.PlannedListChange.Action;
import com.pim.backend.lov.model.ValueId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Planning a bulk load of several lists (feature 013 FR-006–FR-009, FR-012, FR-014; research R-05, R-08). */
class BulkLoadPlanTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-11-20T10:00:00Z");

    private static final ListOfValues COLOR = ListOfValues.create("LOV000001", ListName.of("Color"), BEFORE)
            .withValues(
                    List.of(
                            new ListValue(ValueId.of("ROJ"), "Rojo", Optional.of("RED")),
                            new ListValue(ValueId.of("AZU"), "Azul", Optional.of("BLUE"))),
                    1,
                    BEFORE);

    private static BulkLoadRow row(int number, String list, String id, String label, String equivalence) {
        return new BulkLoadRow(number, list, id, label, equivalence);
    }

    private static BulkLoadPlan plan(List<BulkLoadRow> rows, ListOfValues... existing) {
        Map<String, ListOfValues> byKey = new java.util.HashMap<>();
        for (ListOfValues list : existing) {
            byKey.put(list.name().comparisonKey(), list);
        }
        return BulkLoadPlan.of(rows, name -> Optional.ofNullable(byKey.get(name.comparisonKey())), NOW);
    }

    private static List<ImportRowError> rowErrors(List<BulkLoadRow> rows, ListOfValues... existing) {
        try {
            plan(rows, existing);
        } catch (InvalidImportException exception) {
            return exception.rowErrors();
        }
        throw new AssertionError("The plan accepted the rows");
    }

    @Test
    void createsMissingListsAndUpdatesExistingOnesInFileOrder() {
        BulkLoadPlan plan = plan(
                List.of(
                        row(2, "Color", "AZU", "Azul marino", ""),
                        row(3, "Talla", "0001", "Chica", "S"),
                        row(4, " color ", "VER", "Verde", "GREEN"),
                        row(5, "talla", "", "Mediana", null)),
                COLOR);

        assertThat(plan.planned())
                .containsExactly(
                        new PlannedListChange("Color", Optional.of("LOV000001"), Action.UPDATE, 1, 1),
                        new PlannedListChange("Talla", Optional.empty(), Action.CREATE, 2, 0));
        ListOfValues color = plan.changes().get(0).after();
        assertThat(color.values())
                .extracting(value -> value.id().value(), ListValue::label, value -> value.equivalence())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ROJ", "Rojo", Optional.of("RED")),
                        org.assertj.core.groups.Tuple.tuple("AZU", "Azul marino", Optional.of("BLUE")),
                        org.assertj.core.groups.Tuple.tuple("VER", "Verde", Optional.of("GREEN")));
        assertThat(plan.changes().get(0).before()).contains(COLOR);
        ListOfValues talla = plan.changes().get(1).after();
        assertThat(talla.name().value()).isEqualTo("Talla");
        assertThat(talla.values()).extracting(value -> value.id().value()).containsExactly("0001", "0002");
        assertThat(plan.changes().get(1).before()).isEmpty();
    }

    @Test
    void turnsTheEquivalenceColumnOnForANewListOnlyWhenARowHasOne() {
        BulkLoadPlan plan = plan(List.of(
                row(2, "Talla", "CH", "Chica", "S"),
                row(3, "Marca", "A", "Acme", ""),
                row(4, "Marca", "B", "Beta", null)));

        assertThat(plan.changes())
                .extracting(change -> change.after().equivalencesEnabled())
                .containsExactly(true, false);
    }

    @Test
    void keepsTheEquivalenceSettingOfAnExistingList() {
        assertThat(plan(List.of(row(2, "Color", "VER", "Verde", "GREEN")), COLOR)
                        .changes()
                        .get(0)
                        .after()
                        .equivalencesEnabled())
                .isFalse();
    }

    @Test
    void reportsEveryProblemOfEveryListSortedByRowAndColumn() {
        String longName = "x".repeat(ListName.MAX_LENGTH + 1);

        List<ImportRowError> errors = rowErrors(
                List.of(
                        row(2, "Color", "ROJ", "Rojo", ""),
                        row(3, "", "A", "", ""),
                        row(4, "Talla", "a b", "", ""),
                        row(5, longName, "X", "Equis", ""),
                        row(6, "color", "roj", "Otro rojo", ""),
                        row(7, "Talla", "CH", "Chica", "")),
                COLOR);

        assertThat(errors)
                .extracting(ImportRowError::row, ImportRowError::column, ImportRowError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, "ID", LovErrorCode.DUPLICATE_ID_IN_FILE),
                        org.assertj.core.groups.Tuple.tuple(3, "Lista", LovErrorCode.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple(3, "Etiqueta", LovErrorCode.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple(4, "ID", LovErrorCode.INVALID_VALUE_ID),
                        org.assertj.core.groups.Tuple.tuple(4, "Etiqueta", LovErrorCode.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple(5, "Lista", LovErrorCode.INVALID_LIST_NAME),
                        org.assertj.core.groups.Tuple.tuple(6, "ID", LovErrorCode.DUPLICATE_ID_IN_FILE));
        assertThat(errors.get(0).message())
                .isEqualTo("El ID \"ROJ\" de la lista «Color» está repetido en el archivo (filas 2 y 6).");
    }

    @Test
    void allowsTheSameIdInDifferentLists() {
        assertThat(plan(List.of(row(2, "Color", "A", "Uno", ""), row(3, "Talla", "A", "Uno", "")))
                        .planned())
                .hasSize(2);
    }

    @Test
    void refusesAListThatWouldExceedFiftyThousandValuesNamingIt() {
        List<BulkLoadRow> rows = new ArrayList<>();
        for (int number = 0; number <= ListOfValues.MAX_VALUES - 2; number++) {
            rows.add(row(number + 2, "Color", "V" + number, "Valor", ""));
        }

        assertThatThrownBy(() -> plan(rows, COLOR))
                .isInstanceOf(ListTooLargeException.class)
                .hasMessage("La lista «Color» quedaría con 50,001 valores; una lista puede tener como máximo 50,000."
                        + " No se aplicó ningún valor.");
    }

    @Test
    void givesTheSameTokenOnlyForTheSameFileAndTheSameLists() {
        List<BulkLoadRow> rows = List.of(row(2, "Color", "VER", "Verde", ""));
        byte[] file = "Lista,ID,Etiqueta\nColor,VER,Verde".getBytes(StandardCharsets.UTF_8);
        String token = plan(rows, COLOR).checkToken(file);

        assertThat(plan(rows, COLOR).checkToken(file)).isEqualTo(token).hasSize(64);
        assertThat(plan(rows, COLOR).checkToken("otro".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(token);
        ListOfValues changedColor = COLOR.withEquivalencesEnabled(true, NOW);
        assertThat(plan(rows, changedColor).checkToken(file)).isNotEqualTo(token);
        assertThat(plan(rows).checkToken(file)).isNotEqualTo(token);
    }
}
