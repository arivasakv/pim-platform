package com.pim.backend.attributegroup.model;

/** The user may not change groups: no {@code ATTRIBUTE_CREATE} permission (FR-002; HTTP 403). */
public class GroupChangeNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GroupChangeNotPermittedException() {
        super(AttributeGroupErrorCode.ATTRIBUTE_CREATE_NOT_PERMITTED.message());
    }
}
