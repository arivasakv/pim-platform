package com.pim.backend.lov.model;

/** The list after a successful CSV import, with how many values were added and updated (FR-011). */
public record ImportOutcome(ListOfValues list, int added, int updated) {}
