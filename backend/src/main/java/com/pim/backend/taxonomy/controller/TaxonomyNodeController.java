package com.pim.backend.taxonomy.controller;

import com.pim.backend.taxonomy.dto.AssignAttributesRequest;
import com.pim.backend.taxonomy.dto.AssignAttributesResponse;
import com.pim.backend.taxonomy.dto.TaxonomyNodeCollectionResponse;
import com.pim.backend.taxonomy.dto.TaxonomyNodeCreateRequest;
import com.pim.backend.taxonomy.dto.TaxonomyNodeResponse;
import com.pim.backend.taxonomy.dto.TaxonomyNodeUpdateRequest;
import com.pim.backend.taxonomy.model.NodeDetails;
import com.pim.backend.taxonomy.service.TaxonomyService;
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
 * The product hierarchy and its attribute assignments (contract
 * specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml). HTTP only: the rules are in {@link
 * TaxonomyService} (constitution III). Reading needs no permission; changes need {@code ATTRIBUTE_CREATE} (FR-017).
 */
@RestController
@RequestMapping(TaxonomyNodeController.BASE_PATH)
public class TaxonomyNodeController {

    static final String BASE_PATH = "/api/v1/taxonomy-nodes";

    private final TaxonomyService taxonomyService;
    private final TaxonomyPermissionsReader permissionsReader;

    public TaxonomyNodeController(TaxonomyService taxonomyService, TaxonomyPermissionsReader permissionsReader) {
        this.taxonomyService = taxonomyService;
        this.permissionsReader = permissionsReader;
    }

    @GetMapping
    TaxonomyNodeCollectionResponse list() {
        return TaxonomyNodeCollectionResponse.of(taxonomyService.findAll());
    }

    @PostMapping
    ResponseEntity<TaxonomyNodeResponse> create(
            @Valid @RequestBody TaxonomyNodeCreateRequest request, HttpServletRequest httpRequest) {
        NodeDetails details = taxonomyService.create(
                request.name(), request.description(), request.parentId(), permissionsReader.permissions(httpRequest));
        return ResponseEntity.created(
                        URI.create(BASE_PATH + "/" + details.node().systemId()))
                .body(TaxonomyNodeResponse.from(details));
    }

    @GetMapping("/{nodeId}")
    TaxonomyNodeResponse find(@PathVariable String nodeId) {
        return TaxonomyNodeResponse.from(taxonomyService.find(nodeId));
    }

    @PutMapping("/{nodeId}")
    TaxonomyNodeResponse update(
            @PathVariable String nodeId,
            @Valid @RequestBody TaxonomyNodeUpdateRequest request,
            HttpServletRequest httpRequest) {
        return TaxonomyNodeResponse.from(taxonomyService.update(
                nodeId, request.name(), request.description(), permissionsReader.permissions(httpRequest)));
    }

    @DeleteMapping("/{nodeId}")
    ResponseEntity<Void> delete(@PathVariable String nodeId, HttpServletRequest httpRequest) {
        taxonomyService.delete(nodeId, permissionsReader.permissions(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{nodeId}/attributes")
    AssignAttributesResponse assignAttributes(
            @PathVariable String nodeId,
            @Valid @RequestBody AssignAttributesRequest request,
            HttpServletRequest httpRequest) {
        return AssignAttributesResponse.from(taxonomyService.addAttributes(
                nodeId, request.attributeIds(), permissionsReader.permissions(httpRequest)));
    }

    @DeleteMapping("/{nodeId}/attributes/{attributeId}")
    ResponseEntity<Void> removeAttribute(
            @PathVariable String nodeId, @PathVariable String attributeId, HttpServletRequest httpRequest) {
        taxonomyService.removeAttribute(nodeId, attributeId, permissionsReader.permissions(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
