package com.pim.backend.lov.model;

import java.util.List;

/**
 * What an applied bulk load file changed (feature 013 FR-013, data-model "Result").
 *
 * @param lists every list created or updated, with its system ID, in order of first appearance in the file
 */
public record BulkLoadResult(List<PlannedListChange> lists) {

    public BulkLoadResult {
        lists = List.copyOf(lists);
    }

    public int listsCreated() {
        return BulkLoadSummary.count(lists, PlannedListChange.Action.CREATE);
    }

    public int listsUpdated() {
        return BulkLoadSummary.count(lists, PlannedListChange.Action.UPDATE);
    }

    public int valuesAdded() {
        return lists.stream().mapToInt(PlannedListChange::valuesAdded).sum();
    }

    public int valuesUpdated() {
        return lists.stream().mapToInt(PlannedListChange::valuesUpdated).sum();
    }
}
