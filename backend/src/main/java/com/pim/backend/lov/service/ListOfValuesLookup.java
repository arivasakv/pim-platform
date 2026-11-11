package com.pim.backend.lov.service;

import com.pim.backend.lov.repository.ListOfValuesRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Read-only questions about lists for other domains (research R-03). It reads the repository directly and takes no
 * lock, so attribute creation can call it while holding the attribute lock without risk of deadlock (research R-04).
 */
@Service
public class ListOfValuesLookup {

    private final ListOfValuesRepository lists;

    public ListOfValuesLookup(ListOfValuesRepository lists) {
        this.lists = lists;
    }

    /** Whether at least one list exists. */
    public boolean hasAny() {
        return lists.hasAny();
    }

    /** Whether a list has this system ID, compared ignoring letter case. */
    public boolean exists(String listOfValuesId) {
        return listOfValuesId != null && lists.findById(listOfValuesId.strip()).isPresent();
    }

    /** Every list's name keyed by its system ID upper-cased (feature 007 research R-03). */
    public Map<String, String> namesById() {
        return lists.namesById();
    }
}
