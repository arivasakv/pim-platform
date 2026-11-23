package com.pim.backend.lov.model;

import java.util.Objects;
import java.util.Optional;

/**
 * What a bulk load does, or did, to one list (feature 013 data-model "Planned list change").
 *
 * @param name the list's name, or the name a new list gets
 * @param systemId the list's system ID; empty for a list still to create
 * @param action whether the list is created or updated
 * @param valuesAdded values added to the list
 * @param valuesUpdated existing values whose label (and equivalence) are replaced
 */
public record PlannedListChange(
        String name, Optional<String> systemId, Action action, int valuesAdded, int valuesUpdated) {

    /** Whether the file creates the list or changes an existing one. */
    public enum Action {
        CREATE,
        UPDATE
    }

    public PlannedListChange {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(action, "action");
    }

    /** This change with the system ID the list got when it was created. */
    public PlannedListChange withSystemId(String newSystemId) {
        return new PlannedListChange(name, Optional.of(newSystemId), action, valuesAdded, valuesUpdated);
    }
}
