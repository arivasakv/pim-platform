package com.pim.backend.lov.service;

import com.pim.backend.attribute.repository.ListOfValuesCatalog;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Feature 004's view of the lists that list-of-values attributes may link to (feature 004 FR-012; feature 006
 * FR-026, plan decision D-3). It replaces feature 004's empty placeholder and only delegates to {@link
 * ListOfValuesLookup}, so there is no bean cycle with {@code AttributeService}.
 */
@Component
public class ListOfValuesCatalogAdapter implements ListOfValuesCatalog {

    private final ListOfValuesLookup lookup;

    public ListOfValuesCatalogAdapter(ListOfValuesLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public boolean hasAny() {
        return lookup.hasAny();
    }

    @Override
    public boolean exists(String listOfValuesId) {
        return lookup.exists(listOfValuesId);
    }

    @Override
    public Map<String, String> namesById() {
        return lookup.namesById();
    }
}
