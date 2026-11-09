package com.pim.backend.identifier.controller;

import com.pim.backend.identifier.model.ManualIdPermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Reads the requesting user's manual-ID permissions (FR-003).
 *
 * <p><strong>Development stand-in until authentication exists</strong> (research R-08, plan decision D-7): the
 * permissions arrive in the header {@value #HEADER} as comma-separated object-type codes, and anyone can send it.
 * When authentication is added, only this class changes; request paths and bodies stay the same (constitution
 * VIII).
 */
@Component
public class ManualIdPermissionsReader {

    /** Header with the object-type codes for which the user may give manual IDs. */
    public static final String HEADER = "X-Pim-Manual-Id-Permissions";

    public ManualIdPermissions read(HttpServletRequest request) {
        return ManualIdPermissions.fromCodes(request.getHeader(HEADER));
    }
}
