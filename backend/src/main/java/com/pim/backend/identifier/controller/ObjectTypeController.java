package com.pim.backend.identifier.controller;

import com.pim.backend.identifier.dto.ObjectTypeConfigurationCollectionResponse;
import com.pim.backend.identifier.dto.ObjectTypeConfigurationRequest;
import com.pim.backend.identifier.dto.ObjectTypeConfigurationResponse;
import com.pim.backend.identifier.dto.SequenceDto;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeNotFoundException;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceKind;
import com.pim.backend.identifier.service.IdentifierService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration of each object type: its ID sequence and key fields (FR-006, FR-012, FR-013; contract
 * {@code /api/v1/object-types}). Not restricted by any permission in this feature (plan decision D-4).
 */
@RestController
@RequestMapping("/api/v1/object-types")
public class ObjectTypeController {

    private final IdentifierService identifierService;

    public ObjectTypeController(IdentifierService identifierService) {
        this.identifierService = identifierService;
    }

    @GetMapping
    ObjectTypeConfigurationCollectionResponse findAll() {
        return ObjectTypeConfigurationCollectionResponse.of(identifierService.findConfigurations().stream()
                .map(ObjectTypeConfigurationResponse::from)
                .toList());
    }

    @GetMapping("/{objectType}")
    ObjectTypeConfigurationResponse find(@PathVariable String objectType) {
        return ObjectTypeConfigurationResponse.from(identifierService.findConfiguration(objectTypeOf(objectType)));
    }

    @PutMapping("/{objectType}")
    ObjectTypeConfigurationResponse replace(
            @PathVariable String objectType, @Valid @RequestBody ObjectTypeConfigurationRequest request) {
        ObjectType type = objectTypeOf(objectType);
        SequenceConfiguration sequence = sequenceOf(request.sequence());
        KeyFieldDeclaration keyFields = new KeyFieldDeclaration(request.keyFields());
        return ObjectTypeConfigurationResponse.from(identifierService.replaceConfiguration(type, sequence, keyFields));
    }

    private static ObjectType objectTypeOf(String code) {
        return ObjectType.fromCode(code).orElseThrow(() -> new ObjectTypeNotFoundException(code));
    }

    private static SequenceConfiguration sequenceOf(SequenceDto sequence) {
        SequenceKind kind = SequenceKind.fromCode(sequence.kind())
                .orElseThrow(() -> new InvalidIdentifierRequestException(
                        "sequence.kind", IdentifierErrorCode.INVALID_SEQUENCE_KIND));
        return new SequenceConfiguration(kind, sequence.prefix(), sequence.minimumDigits(), sequence.nextValue());
    }
}
