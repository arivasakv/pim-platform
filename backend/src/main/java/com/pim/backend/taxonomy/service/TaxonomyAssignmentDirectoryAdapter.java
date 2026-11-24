package com.pim.backend.taxonomy.service;

import com.pim.backend.attribute.model.TaxonomyNodeReference;
import com.pim.backend.attribute.repository.TaxonomyAssignmentDirectory;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Feature 004's view of the taxonomy nodes that list attributes (feature 010 plan decision D-7, research R-05). It
 * delegates to {@link TaxonomyService}, which holds the taxonomy lock. The service is looked up when first used
 * ({@link ObjectProvider}), because it depends on {@code AttributeService}, which depends on this adapter.
 */
@Component
public class TaxonomyAssignmentDirectoryAdapter implements TaxonomyAssignmentDirectory {

    private final ObjectProvider<TaxonomyService> taxonomyService;

    public TaxonomyAssignmentDirectoryAdapter(ObjectProvider<TaxonomyService> taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @Override
    public List<TaxonomyNodeReference> runIfOnNoNode(String attributeId, Runnable action) {
        return taxonomyService.getObject().runIfOnNoNode(attributeId, action).stream()
                .map(node -> new TaxonomyNodeReference(node.systemId(), node.name()))
                .toList();
    }

    @Override
    public List<TaxonomyNodeReference> nodesFilteringValuesOf(String attributeId) {
        return taxonomyService.getObject().nodesFilteringValuesOf(attributeId).stream()
                .map(node -> new TaxonomyNodeReference(node.systemId(), node.name()))
                .toList();
    }

    @Override
    public void runRemovingValueFilters(String attributeId, Runnable action) {
        taxonomyService.getObject().runRemovingValueFilters(attributeId, action);
    }
}
