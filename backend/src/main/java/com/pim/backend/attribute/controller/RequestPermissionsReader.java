package com.pim.backend.attribute.controller;

import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.identifier.model.ManualIdPermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Reads the requesting user's permissions (FR-002, FR-019; feature 003 FR-003).
 *
 * <p><strong>Development stand-in until authentication exists</strong> (research R-06, plan decision D-2): the
 * permissions arrive in headers that anyone can send. When authentication is added, only this class changes;
 * request paths and bodies stay the same (constitution VIII).
 */
@Component
public class RequestPermissionsReader {

    /** Header with the user's permission codes, e.g. {@code ATTRIBUTE_CREATE}. */
    public static final String PERMISSIONS_HEADER = "X-Pim-Permissions";

    /** Feature 003's header with the object types for which the user may give manual system IDs. */
    public static final String MANUAL_ID_PERMISSIONS_HEADER = "X-Pim-Manual-Id-Permissions";

    public AttributePermissions attributePermissions(HttpServletRequest request) {
        return AttributePermissions.fromCodes(request.getHeader(PERMISSIONS_HEADER));
    }

    public ManualIdPermissions manualIdPermissions(HttpServletRequest request) {
        return ManualIdPermissions.fromCodes(request.getHeader(MANUAL_ID_PERMISSIONS_HEADER));
    }
}
