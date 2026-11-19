package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.ValueId;
import com.pim.backend.lov.repository.InMemoryListOfValuesRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Read-only list questions for other domains; {@link ListOfValuesLookup#find} is new in feature 012 (R-04). */
class ListOfValuesLookupTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void findsAListWithItsValuesInOrderIgnoringCase() {
        InMemoryListOfValuesRepository lists = new InMemoryListOfValuesRepository();
        lists.insert(new ListOfValues(
                "LOV000001",
                ListName.of("Colores"),
                false,
                List.of(
                        new ListValue(ValueId.of("0002"), "Azul", Optional.empty()),
                        new ListValue(ValueId.of("0001"), "Rojo", Optional.empty())),
                3,
                NOW,
                NOW));
        ListOfValuesLookup lookup = new ListOfValuesLookup(lists);

        ListOfValues found = lookup.find(" lov000001 ").orElseThrow();

        assertThat(found.name().value()).isEqualTo("Colores");
        assertThat(found.values()).extracting(ListValue::label).containsExactly("Azul", "Rojo");
        assertThat(lookup.find("LOV999999")).isEmpty();
        assertThat(lookup.find(null)).isEmpty();
    }
}
