package com.pim.backend.attribute.controller;

import com.pim.backend.attribute.dto.AttributeCollectionResponse;
import com.pim.backend.attribute.dto.AttributeCreationRequest;
import com.pim.backend.attribute.dto.AttributeResponse;
import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.service.AttributeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creation and reading of attribute definitions (FR-001, FR-021; contract {@code /api/v1/attributes}). HTTP only:
 * it maps the request to model values, and the rules are in {@link AttributeService} (constitution III).
 */
@RestController
@RequestMapping(AttributeController.BASE_PATH)
public class AttributeController {

    static final String BASE_PATH = "/api/v1/attributes";

    private final AttributeService attributeService;
    private final RequestPermissionsReader permissionsReader;

    public AttributeController(AttributeService attributeService, RequestPermissionsReader permissionsReader) {
        this.attributeService = attributeService;
        this.permissionsReader = permissionsReader;
    }

    @PostMapping
    ResponseEntity<AttributeResponse> create(
            @Valid @RequestBody AttributeCreationRequest request, HttpServletRequest httpRequest) {
        AttributeDataType dataType = AttributeDataType.fromCode(request.dataType())
                .orElseThrow(() -> new InvalidAttributeException(
                        "dataType", AttributeErrorCode.UNKNOWN_DATA_TYPE, request.dataType()));
        AttributeDraft draft = new AttributeDraft(
                request.systemId(),
                request.displayName(),
                request.description(),
                dataType,
                AttributePropertiesMapper.fromRequest(dataType, request));
        Attribute attribute = attributeService.create(
                draft,
                permissionsReader.attributePermissions(httpRequest),
                permissionsReader.manualIdPermissions(httpRequest));
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + attribute.systemId()))
                .body(AttributeResponse.from(attribute));
    }

    /** Every attribute, sorted by display name ignoring letter case (feature 005 plan decision D-3). */
    @GetMapping
    AttributeCollectionResponse findAll() {
        return AttributeCollectionResponse.of(
                attributeService.findAll().stream().map(AttributeResponse::from).toList());
    }

    @GetMapping("/{systemId}")
    AttributeResponse find(@PathVariable String systemId) {
        return AttributeResponse.from(attributeService.find(systemId));
    }
}
