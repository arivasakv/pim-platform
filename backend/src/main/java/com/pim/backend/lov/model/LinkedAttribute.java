package com.pim.backend.lov.model;

import com.pim.backend.attribute.model.SelectionMode;

/** An attribute of feature 004 that uses a list, as shown in the list's detail (FR-005, FR-014). */
public record LinkedAttribute(String systemId, String displayName, SelectionMode selectionMode) {}
