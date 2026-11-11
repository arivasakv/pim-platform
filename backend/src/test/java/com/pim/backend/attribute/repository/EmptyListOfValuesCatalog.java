package com.pim.backend.attribute.repository;

/**
 * Test fake: a {@link ListOfValuesCatalog} with no lists, for unit tests that build {@code AttributeService} by hand.
 * Production uses the list-of-values feature's catalog (feature 006 plan decision D-3); this class was the
 * placeholder until then (feature 004 plan decision D-12).
 */
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
