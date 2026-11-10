package com.pim.backend.attributegroup.model;

/** A group that still has attributes cannot be deleted (FR-005; HTTP 409). */
public class GroupNotEmptyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GroupNotEmptyException(AttributeGroup group) {
        super(message(group));
    }

    private static String message(AttributeGroup group) {
        int count = group.attributeIds().size();
        if (count == 1) {
            return "El grupo \"%s\" tiene 1 atributo. Quítalo antes de eliminarlo."
                    .formatted(group.name().value());
        }
        return AttributeGroupErrorCode.GROUP_NOT_EMPTY.message(group.name().value(), count);
    }
}
