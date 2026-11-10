package com.pim.backend.attribute.model;

/**
 * Properties of a reference attribute, which links a product to another system object (ERF 1.1 §3.2.1 Referencias
 * o Relaciones; FR-015; plan decision D-11).
 *
 * @param targetObjectType the kind of object referenced; required
 * @param cardinality one object or several
 */
public record ReferenceProperties(ReferenceTargetType targetObjectType, Cardinality cardinality)
        implements AttributeProperties {

    public ReferenceProperties {
        if (targetObjectType == null) {
            throw new InvalidAttributeException("referenceProperties.targetObjectType", AttributeErrorCode.REQUIRED);
        }
        if (cardinality == null) {
            cardinality = Cardinality.SINGLE;
        }
    }

    /** Reference properties with single cardinality when {@code cardinality} is absent. */
    public static ReferenceProperties of(ReferenceTargetType targetObjectType, Cardinality cardinality) {
        return new ReferenceProperties(targetObjectType, cardinality);
    }

    @Override
    public AttributeDataType dataType() {
        return AttributeDataType.REFERENCE;
    }
}
