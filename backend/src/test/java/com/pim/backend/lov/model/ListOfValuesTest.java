package com.pim.backend.lov.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** List rules: values, generated IDs, limits, renaming and equivalences (FR-006–FR-010, FR-023; R-07, R-08). */
class ListOfValuesTest {

    private static final Instant CREATED = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-11T11:00:00Z");

    private final ListOfValues empty = ListOfValues.create("LOV000001", ListName.of("Color"), CREATED);

    @Test
    void startsWithNoValues() {
        assertThat(empty.values()).isEmpty();
        assertThat(empty.nextGeneratedNumber()).isEqualTo(1);
        assertThat(empty.equivalencesEnabled()).isFalse();
        assertThat(empty.createdAt()).isEqualTo(CREATED);
        assertThat(empty.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void addsAValueWithTheIdGiven() {
        ValueAddition addition = empty.withValueAdded("0001", " Azul ", " BLUE ", LATER);

        assertThat(addition.value()).isEqualTo(new ListValue(ValueId.of("0001"), "Azul", Optional.of("BLUE")));
        assertThat(addition.list().values()).containsExactly(addition.value());
        assertThat(addition.list().updatedAt()).isEqualTo(LATER);
    }

    @Test
    void refusesAnIdAlreadyInTheListIgnoringCase() {
        ListOfValues list = empty.withValueAdded("abc", "Uno", null, LATER).list();

        assertThatThrownBy(() -> list.withValueAdded("ABC", "Dos", null, LATER))
                .isInstanceOf(ValueIdInUseException.class)
                .hasMessage("Ya existe un valor con el ID \"ABC\" en esta lista.");
    }

    @Test
    void validatesTheLabelAndTheEquivalence() {
        assertInvalid(() -> empty.withValueAdded("1", null, null, LATER), "label", LovErrorCode.REQUIRED);
        assertInvalid(() -> empty.withValueAdded("1", "  ", null, LATER), "label", LovErrorCode.INVALID_LABEL);
        assertInvalid(
                () -> empty.withValueAdded("1", "x".repeat(201), null, LATER), "label", LovErrorCode.INVALID_LABEL);
        assertInvalid(
                () -> empty.withValueAdded("1", "Azul", "e".repeat(201), LATER),
                "equivalence",
                LovErrorCode.INVALID_EQUIVALENCE);
        assertThat(empty.withValueAdded("1", "x".repeat(200), "e".repeat(200), LATER)
                        .value()
                        .label())
                .hasSize(200);
        assertThat(empty.withValueAdded("1", "Azul", "   ", LATER).value().equivalence())
                .isEmpty();
    }

    @Test
    void generatesZeroPaddedIdsSkippingIdsTakenByHand() {
        ListOfValues list = empty.withValueAdded(null, "Uno", null, LATER).list();
        list = list.withValueAdded("0002", "Dos", null, LATER).list();
        ValueAddition third = list.withValueAdded("  ", "Tres", null, LATER);

        assertThat(list.values().get(0).id().value()).isEqualTo("0001");
        assertThat(third.value().id().value()).isEqualTo("0003");
        assertThat(third.list().nextGeneratedNumber()).isEqualTo(4);
    }

    @Test
    void neverReusesAGeneratedIdAfterADeletion() {
        ListOfValues list = empty.withValueAdded(null, "Uno", null, LATER).list();
        list = list.withValueRemoved("0001", LATER);

        assertThat(list.withValueAdded(null, "Otro", null, LATER).value().id().value())
                .isEqualTo("0002");
    }

    @Test
    void padsGeneratedIdsToAtLeastFourDigits() {
        assertThat(GeneratedValueIds.format(1)).isEqualTo("0001");
        assertThat(GeneratedValueIds.format(9999)).isEqualTo("9999");
        assertThat(GeneratedValueIds.format(10000)).isEqualTo("10000");
    }

    @Test
    void updatesOnlyTheLabelAndTheEquivalence() {
        ListOfValues list = empty.withValueAdded("0001", "Azul", "BLUE", LATER).list();

        ListOfValues updated = list.withValueUpdated("0001", "AZUL", null, LATER);

        assertThat(updated.values()).containsExactly(new ListValue(ValueId.of("0001"), "AZUL", Optional.empty()));
        assertThatThrownBy(() -> list.withValueUpdated("9", "X", null, LATER))
                .isInstanceOf(ValueNotFoundException.class);
    }

    @Test
    void removesAValueAndRefusesAnUnknownOne() {
        ListOfValues list = empty.withValueAdded("0001", "Azul", null, LATER).list();

        assertThat(list.withValueRemoved("0001", LATER).values()).isEmpty();
        assertThatThrownBy(() -> list.withValueRemoved("0002", LATER))
                .isInstanceOf(ValueNotFoundException.class)
                .hasMessage("No existe un valor con el ID 0002 en esta lista.");
    }

    @Test
    void keepsTheOrderInWhichValuesWereAdded() {
        ListOfValues list = empty.withValueAdded("z", "Z", null, LATER).list();
        list = list.withValueAdded("a", "A", null, LATER).list();

        assertThat(list.values()).extracting(value -> value.id().value()).containsExactly("z", "a");
    }

    @Test
    void refusesMoreThanFiftyThousandValues() {
        List<ListValue> values = new ArrayList<>();
        for (int number = 1; number <= ListOfValues.MAX_VALUES; number++) {
            values.add(new ListValue(ValueId.of("v" + number), "Valor", Optional.empty()));
        }
        ListOfValues full = empty.withValues(values, 1, LATER);

        assertThatThrownBy(() -> full.withValueAdded("extra", "X", null, LATER))
                .isInstanceOf(ListTooLargeException.class);
        values.add(new ListValue(ValueId.of("extra"), "X", Optional.empty()));
        assertThatThrownBy(() -> empty.withValues(values, 1, LATER)).isInstanceOf(ListTooLargeException.class);
    }

    @Test
    void renamingAndTurningEquivalencesOffKeepTheValues() {
        ListOfValues list = empty.withEquivalencesEnabled(true, LATER)
                .withValueAdded("0001", "Azul", "BLUE", LATER)
                .list();

        ListOfValues changed = list.renamed(ListName.of("Colores"), LATER).withEquivalencesEnabled(false, LATER);

        assertThat(changed.name().value()).isEqualTo("Colores");
        assertThat(changed.systemId()).isEqualTo("LOV000001");
        assertThat(changed.equivalencesEnabled()).isFalse();
        assertThat(changed.values()).isEqualTo(list.values());
    }

    @Test
    void findsAValueIgnoringCase() {
        ListOfValues list = empty.withValueAdded("abc", "Uno", null, LATER).list();

        assertThat(list.find("ABC")).map(ListValue::label).contains("Uno");
        assertThat(list.find("a b")).isEmpty();
    }

    private static void assertInvalid(Runnable action, String field, LovErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(InvalidListException.class, exception -> {
            assertThat(exception.field()).isEqualTo(field);
            assertThat(exception.code()).isEqualTo(code);
        });
    }
}
