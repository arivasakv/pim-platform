package com.pim.backend.taxonomy.controller;

import com.pim.backend.attribute.model.AttributePermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Reads the requesting user's permissions for taxonomy changes (spec FR-017; plan decision D-8: the same {@code
 * ATTRIBUTE_CREATE} permission as features 004 to 007).
 *
 * <p><strong>Development stand-in until authentication exists</strong> (feature 004 research R-06): the permissions
 * arrive in a header that anyone can send. When authentication is added, only this class changes.
 */
@Component
public class TaxonomyPermissionsReader {

    /** Header with the user's permission codes, e.g. {@code ATTRIBUTE_CREATE} (same header as feature 004). */
    public static final String PERMISSIONS_HEADER = "X-Pim-Permissions";

    public AttributePermissions permissions(HttpServletRequest request) {
        return AttributePermissions.fromCodes(request.getHeader(PERMISSIONS_HEADER));
    }
}
