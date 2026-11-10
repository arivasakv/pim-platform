package com.pim.backend.attribute.repository;

/**
 * The lists of values a list-of-values attribute can link to (FR-012). The list-of-values feature (DEP-03) will
 * provide the real implementation; until then {@link EmptyListOfValuesCatalog} holds none.
 */
public interface ListOfValuesCatalog {

    /** Whether at least one list of values exists. */
    boolean hasAny();

    /** Whether a list of values with this ID exists. */
    boolean exists(String listOfValuesId);
}
