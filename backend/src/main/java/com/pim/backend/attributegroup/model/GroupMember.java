package com.pim.backend.attributegroup.model;

/**
 * One member attribute of a group as shown in the group's details (FR-012): read from feature 004 through its
 * service, never stored in the group.
 *
 * @param systemId the attribute's system ID
 * @param displayName its display name
 * @param dataType its data type code, e.g. {@code NUMERIC}
 */
public record GroupMember(String systemId, String displayName, String dataType) {}
