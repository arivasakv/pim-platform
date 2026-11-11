package com.pim.backend.attribute.service;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeChangeNotPermittedException;
import com.pim.backend.attribute.model.AttributeCreateNotPermittedException;
import com.pim.backend.attribute.model.AttributeDetails;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.AttributeInGroupsException;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.DisplayName;
import com.pim.backend.attribute.model.DisplayNameInUseException;
import com.pim.backend.attribute.model.GroupReference;
import com.pim.backend.attribute.model.ImmutableFieldException;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.repository.AttributeGroupDirectory;
import com.pim.backend.attribute.repository.AttributeRepository;
import com.pim.backend.attribute.repository.ListOfValuesCatalog;
import com.pim.backend.identifier.model.AssignmentOutcome;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Creates, reads, edits and deletes attribute definitions (ERF 1.1 §3.2, §3.2.1; feature 004 FR-001 to FR-023;
 * feature 007 FR-004 to FR-018). Every attribute rule is checked before feature 003 is asked for a system ID, so a
 * refused attribute never uses up an ID (feature 004 research R-03). Feature 003 is used only through {@link
 * IdentifierService} (constitution III). Every change runs under one lock (feature 004 research R-18).
 *
 * <p>Lock order (feature 007 research R-06): list of values, then attribute; group, then attribute. This service
 * never calls {@link AttributeGroupDirectory} while holding its own lock.
 */
@Service
public class AttributeService {

    /** Key field feature 003 may declare for attributes (FR-006). */
    static final String DISPLAY_NAME_KEY_FIELD = "displayName";

    private final AttributeRepository attributes;
    private final ListOfValuesCatalog listsOfValues;
    private final AttributeGroupDirectory groupDirectory;
    private final IdentifierService identifierService;
    private final Clock clock;
    private final Object lock = new Object();

    public AttributeService(
            AttributeRepository attributes,
            ListOfValuesCatalog listsOfValues,
            AttributeGroupDirectory groupDirectory,
            IdentifierService identifierService,
            Clock clock) {
        this.attributes = attributes;
        this.listsOfValues = listsOfValues;
        this.groupDirectory = groupDirectory;
        this.identifierService = identifierService;
        this.clock = clock;
    }

    /**
     * Creates one attribute.
     *
     * @throws AttributeCreateNotPermittedException without the {@code ATTRIBUTE_CREATE} permission
     * @throws com.pim.backend.attribute.model.InvalidAttributeException for an invalid display name, description
     *     or property
     * @throws DisplayNameInUseException when another attribute has the display name, ignoring letter case
     */
    public Attribute create(
            AttributeDraft draft, AttributePermissions permissions, ManualIdPermissions manualIdPermissions) {
        if (!permissions.allowsCreation()) {
            throw new AttributeCreateNotPermittedException();
        }
        DisplayName displayName = DisplayName.of(draft.displayName());
        Optional<String> description = Attribute.normalizedDescription(draft.description());
        synchronized (lock) {
            // Checked under the lock, so a list cannot be deleted between this check and the save (feature 006
            // research R-04). The order of the checks is unchanged.
            if (draft.properties() instanceof ListOfValuesProperties listOfValues) {
                checkListOfValues(listOfValues);
            }
            if (attributes.existsWithDisplayName(displayName)) {
                throw new DisplayNameInUseException(displayName);
            }
            AssignmentResult assignment = identifierService.assign(
                    ObjectType.ATTRIBUTE,
                    draft.requestedSystemId(),
                    declaredKeyFieldValues(displayName),
                    manualIdPermissions);
            if (assignment.outcome() == AssignmentOutcome.SAME_OBJECT) {
                // Not reachable: a duplicate display name is refused above (plan decision D-5).
                throw new DisplayNameInUseException(displayName);
            }
            Attribute attribute = Attribute.create(
                    assignment.registration().systemId().value(),
                    displayName,
                    description.orElse(null),
                    draft.dataType(),
                    draft.properties(),
                    clock.instant());
            attributes.save(attribute);
            return attribute;
        }
    }

    /**
     * The attribute with this system ID, compared ignoring letter case.
     *
     * @throws AttributeNotFoundException if none has it
     */
    public Attribute find(String systemId) {
        synchronized (lock) {
            return attributes.findBySystemId(systemId).orElseThrow(() -> new AttributeNotFoundException(systemId));
        }
    }

    /** Every attribute, sorted by display name ignoring letter case (feature 005 plan decision D-3). */
    public List<Attribute> findAll() {
        synchronized (lock) {
            return attributes.findAll();
        }
    }

    /**
     * Every attribute with its groups and list name, sorted by display name ignoring letter case (feature 007 FR-004).
     */
    public List<AttributeDetails> findAllDetails() {
        return details(findAll());
    }

    /**
     * One attribute with its groups and list name (feature 007 FR-006).
     *
     * @throws AttributeNotFoundException if no attribute has the system ID
     */
    public AttributeDetails findDetails(String systemId) {
        return detailsOf(find(systemId));
    }

    /**
     * The attribute with its groups and list name. Must not be called while holding this service's lock (it asks the
     * group directory, feature 007 research R-06).
     */
    public AttributeDetails detailsOf(Attribute attribute) {
        return details(List.of(attribute)).getFirst();
    }

    /**
     * Edits the display name, description and properties of an attribute (feature 007 FR-007 to FR-011; research
     * R-04). The system ID, data type, scope, creation date and group memberships stay.
     *
     * @throws AttributeChangeNotPermittedException without the {@code ATTRIBUTE_CREATE} permission
     * @throws InvalidAttributeException for an invalid display name, description or list-of-values property
     * @throws AttributeNotFoundException if no attribute has the system ID
     * @throws ImmutableFieldException if the draft names another system ID or data type
     * @throws ListOfValuesUnavailableException if the linked list does not exist
     * @throws DisplayNameInUseException when another attribute has the display name, ignoring letter case
     */
    public AttributeDetails update(String systemId, AttributeDraft draft, AttributePermissions permissions) {
        if (!permissions.allowsCreation()) {
            throw new AttributeChangeNotPermittedException();
        }
        DisplayName displayName = DisplayName.of(draft.displayName());
        Attribute.normalizedDescription(draft.description());
        Attribute updated;
        synchronized (lock) {
            Attribute current =
                    attributes.findBySystemId(systemId).orElseThrow(() -> new AttributeNotFoundException(systemId));
            String requestedSystemId = draft.requestedSystemId();
            if (requestedSystemId != null
                    && !requestedSystemId.isBlank()
                    && !requestedSystemId.strip().equalsIgnoreCase(current.systemId())) {
                throw ImmutableFieldException.systemId();
            }
            if (draft.dataType() != current.dataType()) {
                throw ImmutableFieldException.dataType();
            }
            // Under the lock, as for creation, so the list cannot be deleted in between (feature 006 research R-04).
            if (draft.properties() instanceof ListOfValuesProperties listOfValues) {
                checkListOfValues(listOfValues);
            }
            if (attributes.existsWithDisplayName(displayName, current.systemId())) {
                throw new DisplayNameInUseException(displayName);
            }
            updated = current.updated(displayName, draft.description(), draft.properties(), clock.instant());
            attributes.update(updated);
        }
        return detailsOf(updated);
    }

    /**
     * Deletes an attribute that belongs to no group (feature 007 FR-012 to FR-016; research R-05). Its list of values
     * is kept, and its system ID stays registered in feature 003, so it is never assigned again.
     *
     * @throws AttributeChangeNotPermittedException without the {@code ATTRIBUTE_CREATE} permission
     * @throws AttributeInGroupsException if groups contain the attribute; nothing changes
     * @throws AttributeNotFoundException if no attribute has the system ID
     */
    public void delete(String systemId, AttributePermissions permissions) {
        if (!permissions.allowsCreation()) {
            throw new AttributeChangeNotPermittedException();
        }
        // The group lock is taken first, then this service's lock (research R-06).
        List<GroupReference> groups = groupDirectory.runIfInNoGroup(systemId, () -> {
            synchronized (lock) {
                Attribute attribute =
                        attributes.findBySystemId(systemId).orElseThrow(() -> new AttributeNotFoundException(systemId));
                attributes.delete(attribute.systemId());
            }
        });
        if (!groups.isEmpty()) {
            throw new AttributeInGroupsException(groups);
        }
    }

    private List<AttributeDetails> details(List<Attribute> found) {
        Map<String, List<GroupReference>> groups = groupDirectory.groupsByAttribute();
        boolean anyList =
                found.stream().anyMatch(attribute -> attribute.properties() instanceof ListOfValuesProperties);
        Map<String, String> listNames = anyList ? listsOfValues.namesById() : Map.of();
        return found.stream()
                .map(attribute -> new AttributeDetails(
                        attribute,
                        groups.getOrDefault(attribute.systemId().toUpperCase(Locale.ROOT), List.of()),
                        listName(attribute, listNames)))
                .toList();
    }

    private static Optional<String> listName(Attribute attribute, Map<String, String> listNames) {
        if (attribute.properties() instanceof ListOfValuesProperties listOfValues
                && listOfValues.listOfValuesId() != null) {
            return Optional.ofNullable(
                    listNames.get(listOfValues.listOfValuesId().toUpperCase(Locale.ROOT)));
        }
        return Optional.empty();
    }

    /**
     * The list-of-values attributes linked to this list, compared ignoring letter case, sorted by display name
     * ignoring letter case (feature 006 plan decision D-4).
     */
    public List<Attribute> findLinkedToList(String listOfValuesId) {
        synchronized (lock) {
            return linkedTo(listOfValuesId);
        }
    }

    /**
     * Runs {@code action} only if no attribute is linked to the list, while still holding the attribute lock, so no
     * attribute can be linked to the list in between (feature 006 plan decision D-4, research R-04). Callers must not
     * hold a lock that attribute creation also takes.
     *
     * @return {@code true} if the action ran; {@code false} if an attribute uses the list (the action did not run)
     */
    public boolean runIfNoAttributeLinksTo(String listOfValuesId, Runnable action) {
        synchronized (lock) {
            if (!linkedTo(listOfValuesId).isEmpty()) {
                return false;
            }
            action.run();
            return true;
        }
    }

    private List<Attribute> linkedTo(String listOfValuesId) {
        return attributes.findAll().stream()
                .filter(attribute -> attribute.properties() instanceof ListOfValuesProperties listOfValues
                        && listOfValues.listOfValuesId() != null
                        && listOfValues.listOfValuesId().equalsIgnoreCase(listOfValuesId))
                .toList();
    }

    /**
     * FR-012, in this order: no lists at all (422), missing selection mode or list (400), unknown list (422). An
     * attribute of this type cannot exist without an existing list (ERF 1.1 §3.2.1; clarification 5).
     */
    private void checkListOfValues(ListOfValuesProperties properties) {
        if (!listsOfValues.hasAny()) {
            throw ListOfValuesUnavailableException.noListsExist();
        }
        if (properties.selectionMode() == null) {
            throw new InvalidAttributeException("listOfValuesProperties.selectionMode", AttributeErrorCode.REQUIRED);
        }
        if (properties.listOfValuesId() == null || properties.listOfValuesId().isBlank()) {
            throw new InvalidAttributeException("listOfValuesProperties.listOfValuesId", AttributeErrorCode.REQUIRED);
        }
        if (!listsOfValues.exists(properties.listOfValuesId())) {
            throw ListOfValuesUnavailableException.notFound(properties.listOfValuesId());
        }
    }

    /**
     * The key-field values feature 003 expects for attributes: the display name if feature 003 declares it, none
     * otherwise (FR-006; feature 003 rejects undeclared key fields).
     */
    private Map<String, String> declaredKeyFieldValues(DisplayName displayName) {
        boolean declared = identifierService
                .findConfiguration(ObjectType.ATTRIBUTE)
                .keyFields()
                .fieldNames()
                .contains(DISPLAY_NAME_KEY_FIELD);
        return declared ? Map.of(DISPLAY_NAME_KEY_FIELD, displayName.value()) : Map.of();
    }
}
