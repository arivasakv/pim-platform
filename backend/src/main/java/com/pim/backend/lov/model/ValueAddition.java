package com.pim.backend.lov.model;

/** The list after adding one value, and that value with its final (possibly generated) ID. */
public record ValueAddition(ListOfValues list, ListValue value) {}
