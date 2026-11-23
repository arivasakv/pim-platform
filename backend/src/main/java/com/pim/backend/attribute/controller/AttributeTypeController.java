package com.pim.backend.attribute.controller;

import com.pim.backend.attribute.dto.AttributeTypeCollectionResponse;
import com.pim.backend.attribute.dto.AttributeTypeResponse;
import com.pim.backend.attribute.dto.OptionResponse;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.Cardinality;
import com.pim.backend.attribute.model.CaseFormat;
import com.pim.backend.attribute.model.CurrencyCatalog;
import com.pim.backend.attribute.model.DateFormat;
import com.pim.backend.attribute.model.NumberFormat;
import com.pim.backend.attribute.model.ReferenceTargetType;
import com.pim.backend.attribute.model.SelectionMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The data types and their option lists, so the frontend holds no attribute data (FR-007, FR-022; contract
 * {@code /api/v1/attribute-types}).
 */
@RestController
public class AttributeTypeController {

    @GetMapping("/api/v1/attribute-types")
    AttributeTypeCollectionResponse findAll() {
        return AttributeTypeCollectionResponse.of(Arrays.stream(AttributeDataType.values())
                .map(type -> new AttributeTypeResponse(type.name(), type.label(), optionsOf(type)))
                .toList());
    }

    private static Map<String, List<OptionResponse>> optionsOf(AttributeDataType type) {
        Map<String, List<OptionResponse>> options = new LinkedHashMap<>();
        switch (type) {
            case TEXT -> options.put("caseFormat", options(CaseFormat.values(), CaseFormat::name, CaseFormat::label));
            case NUMERIC ->
                options.put("numberFormat", options(NumberFormat.values(), NumberFormat::name, NumberFormat::label));
            case LIST_OF_VALUES ->
                options.put(
                        "selectionMode", options(SelectionMode.values(), SelectionMode::name, SelectionMode::label));
            case DATE_TIME ->
                options.put("dateFormat", options(DateFormat.values(), DateFormat::value, DateFormat::value));
            case REFERENCE -> {
                options.put(
                        "targetObjectType",
                        options(ReferenceTargetType.values(), ReferenceTargetType::name, ReferenceTargetType::label));
                options.put("cardinality", options(Cardinality.values(), Cardinality::name, Cardinality::label));
            }
            case CURRENCY ->
                options.put(
                        "currencyCode",
                        CurrencyCatalog.inUse().stream()
                                .map(currency ->
                                        new OptionResponse(currency.code(), currency.name(), currency.symbol()))
                                .toList());
            case BOOLEAN -> {
                // No option lists.
            }
        }
        return options;
    }

    private static <E> List<OptionResponse> options(
            E[] values, java.util.function.Function<E, String> code, java.util.function.Function<E, String> label) {
        return Arrays.stream(values)
                .map(value -> new OptionResponse(code.apply(value), label.apply(value)))
                .toList();
    }
}
