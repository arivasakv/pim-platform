package com.pim.backend.attribute.service;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeCreateNotPermittedException;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.DisplayName;
import com.pim.backend.attribute.model.DisplayNameInUseException;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.repository.AttributeRepository;
import com.pim.backend.attribute.repository.ListOfValuesCatalog;
import com.pim.backend.identifier.model.AssignmentOutcome;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Creates attribute definitions (ERF 1.1 §3.2, §3.2.1; spec FR-001 to FR-023). Every attribute rule is checked
 * before feature 003 is asked for a system ID, so a refused attribute never uses up an ID (research R-03). Feature
 * 003 is used only through {@link IdentifierService} (constitution III). "Check display name, assign ID, save" runs
 * under one lock (research R-18).
 */
@Service
public class AttributeService {

    /** Key field feature 003 may declare for attributes (FR-006). */
    static final String DISPLAY_NAME_KEY_FIELD = "displayName";

    private final AttributeRepository attributes;
    private final ListOfValuesCatalog listsOfValues;
    private final IdentifierService identifierService;
    private final Clock clock;
    private final Object lock = new Object();

    public AttributeService(
            AttributeRepository attributes,
            ListOfValuesCatalog listsOfValues,
            IdentifierService identifierService,
            Clock clock) {
        this.attributes = attributes;
        this.listsOfValues = listsOfValues;
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
        if (draft.properties() instanceof ListOfValuesProperties listOfValues) {
            checkListOfValues(listOfValues);
        }
        synchronized (lock) {
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
