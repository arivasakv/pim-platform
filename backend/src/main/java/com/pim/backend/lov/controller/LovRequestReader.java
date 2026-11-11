package com.pim.backend.lov.controller;

import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.identifier.model.ManualIdPermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Reads who is asking and what they may do (FR-002; research R-10, R-11).
 *
 * <p><strong>Development stand-in until authentication exists</strong>: permissions and the user name arrive in
 * headers that anyone can send. When authentication is added, only this class changes; request paths and bodies stay
 * the same (constitution VIII).
 */
@Component
public class LovRequestReader {

    /** Header with the user's permission codes; {@code ATTRIBUTE_CREATE} allows changes (same as feature 004). */
    public static final String PERMISSIONS_HEADER = "X-Pim-Permissions";

    /** Feature 003's manual-ID permissions; {@code LIST_OF_VALUES} allows choosing a list's system ID. */
    public static final String MANUAL_ID_PERMISSIONS_HEADER = "X-Pim-Manual-Id-Permissions";

    /** The user's name, kept in change records (research R-11). */
    public static final String USER_HEADER = "X-Pim-User";

    static final int MAX_USER_LENGTH = 100;

    public AttributePermissions permissions(HttpServletRequest request) {
        return AttributePermissions.fromCodes(request.getHeader(PERMISSIONS_HEADER));
    }

    public ManualIdPermissions manualIdPermissions(HttpServletRequest request) {
        return ManualIdPermissions.fromCodes(request.getHeader(MANUAL_ID_PERMISSIONS_HEADER));
    }

    /** The user name, trimmed and cut to 100 characters; {@code null} when absent or blank. */
    public String user(HttpServletRequest request) {
        String user = request.getHeader(USER_HEADER);
        if (user == null || user.isBlank()) {
            return null;
        }
        String trimmed = user.strip();
        return trimmed.length() > MAX_USER_LENGTH ? trimmed.substring(0, MAX_USER_LENGTH) : trimmed;
    }
}
