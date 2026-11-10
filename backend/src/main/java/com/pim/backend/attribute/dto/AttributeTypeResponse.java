package com.pim.backend.attribute.dto;

import java.util.List;
import java.util.Map;

/**
 * One data type and the option lists its properties offer (FR-007, FR-022).
 *
 * @param code data type code
 * @param label Spanish label
 * @param options option lists keyed by property name; empty when the type has none
 */
public record AttributeTypeResponse(String code, String label, Map<String, List<OptionResponse>> options) {}
