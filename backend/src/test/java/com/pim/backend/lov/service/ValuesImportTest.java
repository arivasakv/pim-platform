package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.pim.backend.lov.model.ImportOutcome;
import com.pim.backend.lov.model.ImportRowError;
import com.pim.backend.lov.model.InvalidImportException;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListTooLargeException;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.LovErrorCode;
import com.pim.backend.lov.model.ValueId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Applying CSV rows to a list, all or nothing (FR-011, FR-016; OQ-005). */
class ValuesImportTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final ListOfValues color = ListOfValues.create("LOV000001", ListName.of("Color"), NOW)
            .withValueAdded(null, "Azul", "BLUE", NOW)
            .list()
            .withValueAdded("abc", "Letras", null, NOW)
            .list();

    @Test
    void addsNewValuesUpdatesExistingOnesAndGeneratesMissingIds() {
        ImportOutcome outcome = ValuesImport.apply(
                color,
                List.of(
                        row(2, "0001", "AZUL", ""),
                        row(3, "ABC", "LETRAS", "L"),
                        row(4, "", "Verde", null),
                        row(5, "x-1", "Equis", "X"),
                        row(6, null, "Rosa", "")),
                NOW);

        assertThat(outcome.added()).isEqualTo(3);
        assertThat(outcome.updated()).isEqualTo(2);
        assertThat(outcome.list().values())
                .extracting(
                        value -> value.id().value(),
                        ListValue::label,
                        value -> value.equivalence().orElse(null))
                .containsExactly(
                        tuple("0001", "AZUL", "BLUE"),
                        tuple("abc", "LETRAS", "L"),
                        tuple("0002", "Verde", null),
                        tuple("x-1", "Equis", "X"),
                        tuple("0003", "Rosa", null));
        assertThat(outcome.list().nextGeneratedNumber()).isEqualTo(4);
    }

    @Test
    void generatedIdsSkipIdsThatAppearLaterInTheFile() {
        ImportOutcome outcome =
                ValuesImport.apply(color, List.of(row(2, "", "Uno", null), row(3, "0002", "Dos", null)), NOW);

        assertThat(outcome.list().values())
                .extracting(value -> value.id().value())
                .containsExactly("0001", "abc", "0003", "0002");
    }

    @Test
    void collectsEveryErrorAndAppliesNothing() {
        List<CsvValuesParser.Row> rows = List.of(
                row(2, "0010", "", null),
                row(3, "a b", "Bien", null),
                row(4, "0020", "x".repeat(201), "e".repeat(201)),
                row(5, "d1", "Uno", null),
                row(6, "0001", " ", null),
                row(7, "D1", "Dos", null));

        assertThatThrownBy(() -> ValuesImport.apply(color, rows, NOW))
                .isInstanceOfSatisfying(InvalidImportException.class, exception -> {
                    assertThat(exception.getMessage())
                            .isEqualTo("El archivo tiene 7 errores. No se aplicó ningún valor.");
                    assertThat(exception.rowErrors())
                            .extracting(ImportRowError::row, ImportRowError::column, ImportRowError::code)
                            .containsExactly(
                                    tuple(2, "Etiqueta", LovErrorCode.REQUIRED),
                                    tuple(3, "ID", LovErrorCode.INVALID_VALUE_ID),
                                    tuple(4, "Etiqueta", LovErrorCode.INVALID_LABEL),
                                    tuple(4, "Equivalencia", LovErrorCode.INVALID_EQUIVALENCE),
                                    tuple(5, "ID", LovErrorCode.DUPLICATE_ID_IN_FILE),
                                    tuple(6, "Etiqueta", LovErrorCode.REQUIRED),
                                    tuple(7, "ID", LovErrorCode.DUPLICATE_ID_IN_FILE));
                    assertThat(exception.rowErrors().get(4).message())
                            .isEqualTo("El ID \"d1\" está repetido en el archivo (filas 5 y 7).");
                });
    }

    @Test
    void refusesAResultOverFiftyThousandValues() {
        List<CsvValuesParser.Row> rows = new ArrayList<>();
        // The list already holds 2 values, so 49,998 new ones reach the limit exactly.
        for (int number = 1; number <= ListOfValues.MAX_VALUES - 2; number++) {
            rows.add(row(number + 1, "n" + number, "Valor", null));
        }
        assertThat(ValuesImport.apply(color, rows, NOW).list().values()).hasSize(ListOfValues.MAX_VALUES);

        rows.add(row(ListOfValues.MAX_VALUES + 1, "last", "Valor", null));
        assertThatThrownBy(() -> ValuesImport.apply(color, rows, NOW)).isInstanceOf(ListTooLargeException.class);
    }

    @Test
    void anEmptyFileChangesNothing() {
        ImportOutcome outcome = ValuesImport.apply(color, List.of(), NOW);

        assertThat(outcome.added()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.list().values())
                .containsExactlyElementsOf(color.values())
                .first()
                .isEqualTo(new ListValue(ValueId.of("0001"), "Azul", Optional.of("BLUE")));
    }

    private static CsvValuesParser.Row row(int number, String id, String label, String equivalence) {
        return new CsvValuesParser.Row(number, id, label, equivalence);
    }
}
