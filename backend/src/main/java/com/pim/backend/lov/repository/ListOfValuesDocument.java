package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.ValueId;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A list of values as stored in the collection {@value #COLLECTION}, with its values embedded so every change is one
 * atomic write (data-model "Collections"; research R-02). Package-private: Spring Data types never leave the
 * repository package (constitution XII).
 *
 * @param id the system ID upper-cased ({@code Locale.ROOT}), so look-ups ignore letter case
 * @param nameKey the name lower-cased; unique index {@value #NAME_INDEX}
 */
@Document(ListOfValuesDocument.COLLECTION)
record ListOfValuesDocument(
        @Id String id,
        String systemId,
        String name,
        String nameKey,
        boolean equivalencesEnabled,
        long nextGeneratedNumber,
        List<ValueDocument> values,
        Instant createdAt,
        Instant updatedAt) {

    static final String COLLECTION = "lists_of_values";
    static final String NAME_KEY = "nameKey";
    static final String NAME_INDEX = "lov_name_unique";

    /** One value inside the list document; {@code idKey} is the ID upper-cased. */
    record ValueDocument(String id, String idKey, String label, String equivalence) {}

    static String idOf(String systemId) {
        return systemId.toUpperCase(Locale.ROOT);
    }

    static ListOfValuesDocument from(ListOfValues list) {
        return new ListOfValuesDocument(
                idOf(list.systemId()),
                list.systemId(),
                list.name().value(),
                list.name().comparisonKey(),
                list.equivalencesEnabled(),
                list.nextGeneratedNumber(),
                list.values().stream()
                        .map(value -> new ValueDocument(
                                value.id().value(),
                                value.id().key(),
                                value.label(),
                                value.equivalence().orElse(null)))
                        .toList(),
                list.createdAt(),
                list.updatedAt());
    }

    ListOfValues toList() {
        List<ListValue> listValues = values == null
                ? List.of()
                : values.stream()
                        .map(value -> new ListValue(
                                ValueId.of(value.id()), value.label(), Optional.ofNullable(value.equivalence())))
                        .toList();
        return new ListOfValues(
                systemId,
                ListName.of(name),
                equivalencesEnabled,
                listValues,
                nextGeneratedNumber,
                createdAt,
                updatedAt);
    }
}
