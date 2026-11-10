package com.pim.backend.attributegroup.model;

/** No group has the requested identifier (FR-011; HTTP 404). */
public class GroupNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GroupNotFoundException(String groupId) {
        super(AttributeGroupErrorCode.GROUP_NOT_FOUND.message(groupId));
    }
}
