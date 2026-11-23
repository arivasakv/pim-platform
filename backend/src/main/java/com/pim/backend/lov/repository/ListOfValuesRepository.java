package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Where lists of values are kept (constitution IX: behind an interface; data-model "Collections"). */
public interface ListOfValuesRepository {

    /** The list with this system ID, compared ignoring letter case. */
    Optional<ListOfValues> findById(String systemId);

    /** Every list, sorted by name ignoring letter case. */
    List<ListOfValues> findAll();

    /** Every list's name keyed by its system ID upper-cased, without loading the values (feature 007 research R-03). */
    Map<String, String> namesById();

    /** The list with this name, compared ignoring letter case (feature 013 FR-006). */
    Optional<ListOfValues> findByName(ListName name);

    /** Whether a list other than {@code excludedId} (may be {@code null}) has the name, ignoring letter case. */
    boolean existsWithName(ListName name, String excludedId);

    /** Whether at least one list exists. */
    boolean hasAny();

    /**
     * Stores a new list.
     *
     * @throws IllegalStateException if a list with the same ID or name is already stored
     */
    void insert(ListOfValues list);

    /**
     * Replaces a stored list, values included, in one atomic write (research R-02).
     *
     * @throws IllegalStateException if another list already has the name
     */
    void update(ListOfValues list);

    /** Deletes the list and its values; nothing happens if it does not exist. */
    void delete(String systemId);
}
