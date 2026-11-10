package com.pim.backend.attribute.model;

/**
 * Properties of a list-of-values attribute (ERF 1.1 §3.2.1 Listas de Valores; FR-012; plan decision D-12). Both
 * fields are needed to create the attribute, but they may be absent here: the service first checks that lists
 * exist (422), then that both are given (400), then that the list exists (422) — data-model
 * "ListOfValuesProperties".
 *
 * @param selectionMode single or multiple selection
 * @param listOfValuesId the existing list the attribute uses
 */
public record ListOfValuesProperties(SelectionMode selectionMode, String listOfValuesId)
        implements AttributeProperties {

    @Override
    public AttributeDataType dataType() {
        return AttributeDataType.LIST_OF_VALUES;
    }
}
