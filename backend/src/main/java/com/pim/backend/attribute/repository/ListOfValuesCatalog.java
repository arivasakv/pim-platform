package com.pim.backend.attribute.repository;

import java.util.Map;

/**
 * The lists of values a list-of-values attribute can link to (FR-012). Implemented by the list-of-values feature
 * (feature 006, {@code ListOfValuesCatalogAdapter}).
 */
public interface ListOfValuesCatalog {

    /** Whether at least one list of values exists. */
    boolean hasAny();

    /** Whether a list of values with this ID exists. */
    boolean exists(String listOfValuesId);

    /** Every list's name, keyed by its system ID upper-cased (feature 007 research R-03). */
    Map<String, String> namesById();
}
