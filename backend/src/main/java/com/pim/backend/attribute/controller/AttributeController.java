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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creation, reading, editing and deletion of attribute definitions (feature 004 FR-001, FR-021; feature 007 FR-004 to
 * FR-016; contract {@code /api/v1/attributes} v1.2.0). HTTP only: it maps the request to model values, and the rules
 * are in {@link AttributeService} (constitution III).
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
        Attribute attribute = attributeService.create(
                draft(request),
                permissionsReader.attributePermissions(httpRequest),
                permissionsReader.manualIdPermissions(httpRequest));
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + attribute.systemId()))
                .body(AttributeResponse.from(attributeService.detailsOf(attribute)));
    }

    /**
     * Every attribute with its groups and list name, sorted by display name ignoring letter case (feature 005 plan
     * decision D-3; feature 007 FR-004).
     */
    @GetMapping
    AttributeCollectionResponse findAll() {
        return AttributeCollectionResponse.of(attributeService.findAllDetails().stream()
                .map(AttributeResponse::from)
                .toList());
    }

    @GetMapping("/{systemId}")
    AttributeResponse find(@PathVariable String systemId) {
        return AttributeResponse.from(attributeService.findDetails(systemId));
    }

    /**
     * Edits an attribute (feature 007 FR-007 to FR-011, research R-04). The body has the creation fields; {@code
     * systemId} is optional and must be the attribute's own, and {@code dataType} must be its current type.
     */
    @PutMapping("/{systemId}")
    AttributeResponse update(
            @PathVariable String systemId,
            @Valid @RequestBody AttributeCreationRequest request,
            HttpServletRequest httpRequest) {
        return AttributeResponse.from(
                attributeService.update(systemId, draft(request), permissionsReader.attributePermissions(httpRequest)));
    }

    /** Deletes an attribute that belongs to no group (feature 007 FR-012 to FR-016, research R-05). */
    @DeleteMapping("/{systemId}")
    ResponseEntity<Void> delete(@PathVariable String systemId, HttpServletRequest httpRequest) {
        attributeService.delete(systemId, permissionsReader.attributePermissions(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** The request as model values; unknown option texts and malformed properties are refused here (400). */
    private static AttributeDraft draft(AttributeCreationRequest request) {
        AttributeDataType dataType = AttributeDataType.fromCode(request.dataType())
                .orElseThrow(() -> new InvalidAttributeException(
                        "dataType", AttributeErrorCode.UNKNOWN_DATA_TYPE, request.dataType()));
        return new AttributeDraft(
                request.systemId(),
                request.displayName(),
                dataType,
                AttributePropertiesMapper.fromRequest(dataType, request));
    }
}
