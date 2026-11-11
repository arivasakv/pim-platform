package com.pim.backend.lov.service;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.model.ImportOutcome;
import com.pim.backend.lov.model.LinkedAttribute;
import com.pim.backend.lov.model.ListChangeNotPermittedException;
import com.pim.backend.lov.model.ListDetails;
import com.pim.backend.lov.model.ListInUseException;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListNameInUseException;
import com.pim.backend.lov.model.ListNotFoundException;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListSummary;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.ValueAddition;
import com.pim.backend.lov.repository.ListOfValuesRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Manages lists of values and their values (spec FR-001–FR-014, FR-016, FR-019, FR-023; data-model "State and
 * operation order"). Lists get their system IDs from feature 003 and read attributes from feature 004, both only
 * through their services (constitution III). Every change runs under one lock (research R-12); deleting a list then
 * takes the attribute lock through {@link AttributeService#runIfNoAttributeLinksTo}, always in that order (R-04).
 */
@Service
public class ListOfValuesService {

    private final ListOfValuesRepository lists;
    private final IdentifierService identifierService;
    private final AttributeService attributeService;
    private final ChangeRecorder changeRecorder;
    private final Clock clock;
    private final Object lock = new Object();

    public ListOfValuesService(
            ListOfValuesRepository lists,
            IdentifierService identifierService,
            AttributeService attributeService,
            ChangeRecorder changeRecorder,
            Clock clock) {
        this.lists = lists;
        this.identifierService = identifierService;
        this.attributeService = attributeService;
        this.changeRecorder = changeRecorder;
        this.clock = clock;
    }

    /**
     * Creates an empty list with a feature 003 system ID of type {@code LIST_OF_VALUES} (FR-001).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws com.pim.backend.lov.model.InvalidListException for an invalid name
     * @throws ListNameInUseException when another list has the name, ignoring letter case
     * @throws com.pim.backend.identifier.model.ManualIdNotPermittedException for a manual ID without feature 003's
     *     permission
     */
    public ListDetails create(
            String name,
            String requestedSystemId,
            AttributePermissions permissions,
            ManualIdPermissions manualIdPermissions) {
        checkPermission(permissions);
        ListName listName = ListName.of(name);
        synchronized (lock) {
            if (lists.existsWithName(listName, null)) {
                throw new ListNameInUseException(listName);
            }
            AssignmentResult assignment = identifierService.assign(
                    ObjectType.LIST_OF_VALUES, requestedSystemId, Map.of(), manualIdPermissions);
            ListOfValues list =
                    ListOfValues.create(assignment.registration().systemId().value(), listName, clock.instant());
            lists.insert(list);
            return new ListDetails(list, List.of());
        }
    }

    /** Every list with its number of linked attributes, sorted by name ignoring letter case (User Story 2). */
    public List<ListSummary> findAll() {
        synchronized (lock) {
            Map<String, Long> attributesByList = attributeService.findAll().stream()
                    .map(Attribute::properties)
                    .filter(ListOfValuesProperties.class::isInstance)
                    .map(ListOfValuesProperties.class::cast)
                    .filter(properties -> properties.listOfValuesId() != null)
                    .collect(Collectors.groupingBy(
                            properties -> properties.listOfValuesId().toUpperCase(Locale.ROOT), Collectors.counting()));
            return lists.findAll().stream()
                    .map(list -> new ListSummary(
                            list,
                            attributesByList
                                    .getOrDefault(list.systemId().toUpperCase(Locale.ROOT), 0L)
                                    .intValue()))
                    .toList();
        }
    }

    /**
     * One list with its values and linked attributes (FR-005, FR-014).
     *
     * @throws ListNotFoundException if no list has the ID
     */
    public ListDetails find(String listId) {
        synchronized (lock) {
            return details(existing(listId));
        }
    }

    /**
     * Renames the list and turns its equivalence column on or off; values and links stay (FR-001, FR-023).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws ListNotFoundException if no list has the ID
     * @throws ListNameInUseException when another list has the name, ignoring letter case
     */
    public ListDetails update(
            String listId, String name, boolean equivalencesEnabled, AttributePermissions permissions) {
        checkPermission(permissions);
        ListName listName = ListName.of(name);
        synchronized (lock) {
            ListOfValues list = existing(listId);
            if (lists.existsWithName(listName, list.systemId())) {
                throw new ListNameInUseException(listName);
            }
            ListOfValues changed = list.renamed(listName, clock.instant())
                    .withEquivalencesEnabled(equivalencesEnabled, clock.instant());
            lists.update(changed);
            return details(changed);
        }
    }

    /**
     * Deletes a list and its values, only when no attribute uses it (FR-004; research R-04).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws ListNotFoundException if no list has the ID
     * @throws ListInUseException if attributes use the list; nothing is deleted
     */
    public void delete(String listId, String user, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            ListOfValues list = existing(listId);
            boolean deleted =
                    attributeService.runIfNoAttributeLinksTo(list.systemId(), () -> lists.delete(list.systemId()));
            if (!deleted) {
                throw new ListInUseException(
                        list.name().value(),
                        attributeService.findLinkedToList(list.systemId()).size());
            }
            changeRecorder.listDeleted(list, user);
        }
    }

    /**
     * Adds one value; a missing ID is generated (FR-009, FR-010).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws ListNotFoundException if no list has the ID
     * @throws com.pim.backend.lov.model.ValueIdInUseException when the list already has the value ID
     */
    public ListValue addValue(
            String listId, String id, String label, String equivalence, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            ValueAddition addition = existing(listId).withValueAdded(id, label, equivalence, clock.instant());
            lists.update(addition.list());
            return addition.value();
        }
    }

    /**
     * Replaces a value's label and equivalence; its ID never changes (FR-010, FR-012).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws ListNotFoundException if no list has the ID
     * @throws com.pim.backend.lov.model.ValueNotFoundException if the list has no such value
     */
    public ListValue updateValue(
            String listId, String valueId, String label, String equivalence, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            ListOfValues changed = existing(listId).withValueUpdated(valueId, label, equivalence, clock.instant());
            lists.update(changed);
            return changed.find(valueId).orElseThrow();
        }
    }

    /**
     * Deletes one value (FR-013) and records who did it (FR-019).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws ListNotFoundException if no list has the ID
     * @throws com.pim.backend.lov.model.ValueNotFoundException if the list has no such value
     */
    public void deleteValue(String listId, String valueId, String user, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            ListOfValues list = existing(listId);
            String storedId =
                    list.find(valueId).map(value -> value.id().value()).orElse(valueId);
            ListOfValues changed = list.withValueRemoved(valueId, clock.instant());
            lists.update(changed);
            changeRecorder.valueDeleted(changed, storedId, user);
        }
    }

    /**
     * Adds or updates values from a CSV file, all or nothing (FR-011, FR-016), and records the import (FR-019).
     *
     * @throws ListChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws ListNotFoundException if no list has the ID
     * @throws com.pim.backend.lov.model.InvalidImportFileException for an unreadable or too large file
     * @throws com.pim.backend.lov.model.InvalidImportException listing every wrong row; nothing is applied
     * @throws com.pim.backend.lov.model.ListTooLargeException if the list would exceed 50,000 values
     */
    public ImportOutcome importValues(String listId, byte[] content, String user, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            ListOfValues list = existing(listId);
            List<CsvValuesParser.Row> rows = CsvValuesParser.parse(content);
            ImportOutcome outcome = ValuesImport.apply(list, rows, clock.instant());
            lists.update(outcome.list());
            changeRecorder.valuesImported(outcome.list(), outcome.added(), outcome.updated(), user);
            return outcome;
        }
    }

    private static void checkPermission(AttributePermissions permissions) {
        if (!permissions.allowsCreation()) {
            throw new ListChangeNotPermittedException();
        }
    }

    private ListOfValues existing(String listId) {
        return lists.findById(listId).orElseThrow(() -> new ListNotFoundException(listId));
    }

    private ListDetails details(ListOfValues list) {
        Function<Attribute, LinkedAttribute> linked = attribute -> new LinkedAttribute(
                attribute.systemId(),
                attribute.displayName().value(),
                ((ListOfValuesProperties) attribute.properties()).selectionMode());
        return new ListDetails(
                list,
                attributeService.findLinkedToList(list.systemId()).stream()
                        .map(linked)
                        .toList());
    }
}
