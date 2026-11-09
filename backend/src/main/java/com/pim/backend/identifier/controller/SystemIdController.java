package com.pim.backend.identifier.controller;

import com.pim.backend.identifier.dto.SystemIdAssignmentRequest;
import com.pim.backend.identifier.dto.SystemIdAssignmentResponse;
import com.pim.backend.identifier.dto.SystemIdRegistrationResponse;
import com.pim.backend.identifier.model.AssignmentOutcome;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SystemIdImmutableException;
import com.pim.backend.identifier.service.IdentifierService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System IDs of new objects (FR-012; contract {@code /api/v1/system-ids}). HTTP only: the rules are in
 * {@link IdentifierService} (constitution III).
 */
@RestController
@RequestMapping(SystemIdController.BASE_PATH)
public class SystemIdController {

    static final String BASE_PATH = "/api/v1/system-ids";

    private final IdentifierService identifierService;
    private final ManualIdPermissionsReader permissionsReader;

    public SystemIdController(IdentifierService identifierService, ManualIdPermissionsReader permissionsReader) {
        this.identifierService = identifierService;
        this.permissionsReader = permissionsReader;
    }

    @PostMapping
    ResponseEntity<SystemIdAssignmentResponse> assign(
            @Valid @RequestBody SystemIdAssignmentRequest request, HttpServletRequest httpRequest) {
        ObjectType objectType = ObjectType.fromCode(request.objectType())
                .orElseThrow(() -> new InvalidIdentifierRequestException(
                        "objectType", IdentifierErrorCode.UNKNOWN_OBJECT_TYPE, request.objectType()));
        AssignmentResult result = identifierService.assign(
                objectType, request.systemId(), request.keyFieldValues(), permissionsReader.read(httpRequest));
        SystemIdAssignmentResponse body = SystemIdAssignmentResponse.from(result);
        if (result.outcome() == AssignmentOutcome.ASSIGNED) {
            return ResponseEntity.created(
                            URI.create(BASE_PATH + "/" + body.registration().systemId()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{systemId}")
    SystemIdRegistrationResponse find(@PathVariable String systemId) {
        return SystemIdRegistrationResponse.from(identifierService.find(systemId));
    }

    /** A system ID can never be changed (FR-007): every {@code PUT} is rejected, whatever its body. */
    @PutMapping("/{systemId}")
    void replace(@PathVariable String systemId) {
        throw new SystemIdImmutableException(systemId);
    }

    /** A system ID can never be changed (FR-007): every {@code PATCH} is rejected, whatever its body. */
    @PatchMapping("/{systemId}")
    void modify(@PathVariable String systemId) {
        throw new SystemIdImmutableException(systemId);
    }
}
