package com.pim.backend.identifier.dto;

import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import java.util.List;

/**
 * One object type's configuration as returned by the API.
 *
 * @param objectType object-type code
 * @param sequence the ID sequence
 * @param keyFields the declared key fields
 */
public record ObjectTypeConfigurationResponse(String objectType, SequenceDto sequence, List<String> keyFields) {

    public static ObjectTypeConfigurationResponse from(ObjectTypeConfiguration configuration) {
        return new ObjectTypeConfigurationResponse(
                configuration.objectType().name(),
                SequenceDto.from(configuration.sequence()),
                configuration.keyFields().fieldNames());
    }
}
