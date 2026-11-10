package com.pim.backend.attribute.repository;

import org.springframework.stereotype.Repository;

/**
 * {@link ListOfValuesCatalog} with no lists, used until the list-of-values feature exists (DEP-03; plan decision
 * D-12). Every list-of-values attribute is therefore refused with "no lists exist yet". Replace this class when
 * that feature provides lists.
 */
@Repository
public class EmptyListOfValuesCatalog implements ListOfValuesCatalog {

    @Override
    public boolean hasAny() {
        return false;
    }

    @Override
    public boolean exists(String listOfValuesId) {
        return false;
    }
}
