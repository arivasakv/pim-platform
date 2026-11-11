package com.pim.backend.lov.model;

/**
 * One wrong row of a CSV import (FR-011, FR-016; contract {@code rowErrors}).
 *
 * @param row row number in the file, counting the header as row 1
 * @param column {@code ID}, {@code Etiqueta} or {@code Equivalencia}
 */
public record ImportRowError(int row, String column, LovErrorCode code, String message) {}
