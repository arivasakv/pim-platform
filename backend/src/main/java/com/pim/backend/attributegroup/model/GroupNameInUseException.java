package com.pim.backend.attributegroup.model;

/** Another group already has this name, ignoring letter case (FR-003; HTTP 409). */
public class GroupNameInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GroupNameInUseException(GroupName name) {
        super(AttributeGroupErrorCode.GROUP_NAME_IN_USE.message(name.value()));
    }
}
