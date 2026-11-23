package com.pim.backend.lov.model;

import java.util.List;

/**
 * What a checked bulk load file would change; nothing is saved yet (feature 013 FR-010, data-model "Summary").
 *
 * @param lists the planned changes, in order of first appearance in the file
 * @param checkToken identifies the file and the state of the lists it touches; sent back to apply it (research R-05)
 */
public record BulkLoadSummary(List<PlannedListChange> lists, String checkToken) {

    public BulkLoadSummary {
        lists = List.copyOf(lists);
    }

    public int listsToCreate() {
        return count(lists, PlannedListChange.Action.CREATE);
    }

    public int listsToUpdate() {
        return count(lists, PlannedListChange.Action.UPDATE);
    }

    public int valuesToAdd() {
        return lists.stream().mapToInt(PlannedListChange::valuesAdded).sum();
    }

    public int valuesToUpdate() {
        return lists.stream().mapToInt(PlannedListChange::valuesUpdated).sum();
    }

    static int count(List<PlannedListChange> lists, PlannedListChange.Action action) {
        return (int) lists.stream().filter(list -> list.action() == action).count();
    }
}
