package com.pim.backend.attributegroup.controller;

import com.pim.backend.attribute.model.AttributePermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Reads the requesting user's permissions for group changes (FR-002; clarification 3: the same {@code
 * ATTRIBUTE_CREATE} permission as feature 004).
 *
 * <p><strong>Development stand-in until authentication exists</strong> (feature 004 research R-06): the permissions
 * arrive in a header that anyone can send. When authentication is added, only this class changes; request paths and
 * bodies stay the same (constitution VIII).
 */
@Component
public class GroupPermissionsReader {

    /** Header with the user's permission codes, e.g. {@code ATTRIBUTE_CREATE} (same header as feature 004). */
    public static final String PERMISSIONS_HEADER = "X-Pim-Permissions";

    public AttributePermissions permissions(HttpServletRequest request) {
        return AttributePermissions.fromCodes(request.getHeader(PERMISSIONS_HEADER));
    }
}
