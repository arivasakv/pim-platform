package com.pim.backend.lov.model;

/**
 * One data row of a bulk load file (feature 013 data-model "Bulk load row"), as written: cells are checked by the
 * bulk load plan, not here.
 *
 * @param number the row number the user sees in the file (the header is row 1)
 * @param list the Lista cell
 * @param id the ID cell; empty means "generate one"
 * @param label the Etiqueta cell
 * @param equivalence the Equivalencia cell; {@code null} when the file has no such column
 */
public record BulkLoadRow(int number, String list, String id, String label, String equivalence) {}
