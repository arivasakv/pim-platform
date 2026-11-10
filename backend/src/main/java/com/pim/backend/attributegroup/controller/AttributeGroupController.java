package com.pim.backend.attributegroup.controller;

import com.pim.backend.attributegroup.dto.AddAttributesRequest;
import com.pim.backend.attributegroup.dto.AddAttributesResponse;
import com.pim.backend.attributegroup.dto.AttributeGroupCollectionResponse;
import com.pim.backend.attributegroup.dto.AttributeGroupRequest;
import com.pim.backend.attributegroup.dto.AttributeGroupResponse;
import com.pim.backend.attributegroup.model.GroupDetails;
import com.pim.backend.attributegroup.service.AttributeGroupService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attribute groups and their members (contract specs/005-attribute-groups/contracts/attribute-groups.openapi.yaml).
 * HTTP only: the rules are in {@link AttributeGroupService} (constitution III). Reading needs no permission;
 * changes need {@code ATTRIBUTE_CREATE} (FR-002).
 */
@RestController
@RequestMapping(AttributeGroupController.BASE_PATH)
public class AttributeGroupController {

    static final String BASE_PATH = "/api/v1/attribute-groups";

    private final AttributeGroupService groupService;
    private final GroupPermissionsReader permissionsReader;

    public AttributeGroupController(AttributeGroupService groupService, GroupPermissionsReader permissionsReader) {
        this.groupService = groupService;
        this.permissionsReader = permissionsReader;
    }

    /** The groups sorted by name; with {@code attributeId}, only those containing that attribute (D-8). */
    @GetMapping
    AttributeGroupCollectionResponse list(@RequestParam(required = false) String attributeId) {
        return AttributeGroupCollectionResponse.of(
                attributeId == null ? groupService.findAll() : groupService.findAllContaining(attributeId));
    }

    @PostMapping
    ResponseEntity<AttributeGroupResponse> create(
            @Valid @RequestBody AttributeGroupRequest request, HttpServletRequest httpRequest) {
        GroupDetails details =
                groupService.create(request.name(), request.description(), permissionsReader.permissions(httpRequest));
        return ResponseEntity.created(
                        URI.create(BASE_PATH + "/" + details.group().id()))
                .body(AttributeGroupResponse.from(details));
    }

    @GetMapping("/{groupId}")
    AttributeGroupResponse find(@PathVariable String groupId) {
        return AttributeGroupResponse.from(groupService.find(groupId));
    }

    @PutMapping("/{groupId}")
    AttributeGroupResponse update(
            @PathVariable String groupId,
            @Valid @RequestBody AttributeGroupRequest request,
            HttpServletRequest httpRequest) {
        return AttributeGroupResponse.from(groupService.update(
                groupId, request.name(), request.description(), permissionsReader.permissions(httpRequest)));
    }

    @DeleteMapping("/{groupId}")
    ResponseEntity<Void> delete(@PathVariable String groupId, HttpServletRequest httpRequest) {
        groupService.delete(groupId, permissionsReader.permissions(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/attributes")
    AddAttributesResponse addAttributes(
            @PathVariable String groupId,
            @Valid @RequestBody AddAttributesRequest request,
            HttpServletRequest httpRequest) {
        return AddAttributesResponse.from(groupService.addAttributes(
                groupId, request.attributeIds(), permissionsReader.permissions(httpRequest)));
    }

    @DeleteMapping("/{groupId}/attributes/{attributeId}")
    ResponseEntity<Void> removeAttribute(
            @PathVariable String groupId, @PathVariable String attributeId, HttpServletRequest httpRequest) {
        groupService.removeAttribute(groupId, attributeId, permissionsReader.permissions(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
