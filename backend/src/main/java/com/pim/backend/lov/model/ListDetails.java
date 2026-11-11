package com.pim.backend.lov.model;

import java.util.List;

/** A list with the attributes that use it, sorted by display name (User Story 2). */
public record ListDetails(ListOfValues list, List<LinkedAttribute> attributes) {}
