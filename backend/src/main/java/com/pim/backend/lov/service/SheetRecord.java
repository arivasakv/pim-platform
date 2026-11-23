package com.pim.backend.lov.service;

import java.util.List;

/**
 * One row of a CSV file or an Excel sheet as cell texts (feature 013 research R-02, R-03).
 *
 * @param number the row number the user sees in the file; the header is row 1
 * @param cells the cell texts, left to right; missing trailing cells are absent
 */
record SheetRecord(int number, List<String> cells) {

    SheetRecord {
        cells = List.copyOf(cells);
    }

    /** Whether every cell is blank (an empty line or row). */
    boolean isBlank() {
        return cells.stream().allMatch(String::isBlank);
    }
}
