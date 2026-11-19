package com.pim.backend.taxonomy.service;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.model.AssignmentResult;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.taxonomy.model.AddAttributesOutcome;
import com.pim.backend.taxonomy.model.AttributeNotDirectException;
import com.pim.backend.taxonomy.model.AttributeNotOnNodeException;
import com.pim.backend.taxonomy.model.EffectiveAttribute;
import com.pim.backend.taxonomy.model.EffectiveAttributes;
import com.pim.backend.taxonomy.model.MoveOutcome;
import com.pim.backend.taxonomy.model.NodeAttribute;
import com.pim.backend.taxonomy.model.NodeDetails;
import com.pim.backend.taxonomy.model.NodeMoveNotAllowedException;
import com.pim.backend.taxonomy.model.NodeName;
import com.pim.backend.taxonomy.model.NodeNameInUseException;
import com.pim.backend.taxonomy.model.NodeNotEmptyException;
import com.pim.backend.taxonomy.model.NodeNotFoundException;
import com.pim.backend.taxonomy.model.NodeReference;
import com.pim.backend.taxonomy.model.NodeSummary;
import com.pim.backend.taxonomy.model.TaxonomyChangeNotPermittedException;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import com.pim.backend.taxonomy.model.UnknownAttributeException;
import com.pim.backend.taxonomy.repository.TaxonomyNodeRepository;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The product hierarchy and its attribute inheritance (spec FR-001 to FR-021; plan decisions D-1 to D-9), and moving
 * nodes with their branch (feature 011; {@link #move}). Attributes
 * come from feature 004 and are used only through {@link AttributeService}; node IDs come from feature 003 through
 * {@link IdentifierService} (constitution III). Effective attributes are computed on every read from the current
 * tree (D-4), so ancestor changes reach every descendant at once (FR-015).
 *
 * <p>Every change and read runs under one lock. Lock order (research R-05): group → taxonomy → attribute; this
 * service never calls the attribute group service.
 */
@Service
public class TaxonomyService {

    private static final Logger LOG = LoggerFactory.getLogger(TaxonomyService.class);
    private static final String NAME_KEY_FIELD = "name";
    private static final ManualIdPermissions NO_MANUAL_IDS = new ManualIdPermissions(Set.of());

    private final TaxonomyNodeRepository nodes;
    private final AttributeService attributeService;
    private final IdentifierService identifierService;
    private final Clock clock;
    private final Object lock = new Object();

    public TaxonomyService(
            TaxonomyNodeRepository nodes,
            AttributeService attributeService,
            IdentifierService identifierService,
            Clock clock) {
        this.nodes = nodes;
        this.attributeService = attributeService;
        this.identifierService = identifierService;
        this.clock = clock;
    }

    /** Every node with its level and number of children, sorted by name ignoring letter case (FR-018). */
    public List<NodeSummary> findAll() {
        synchronized (lock) {
            List<TaxonomyNode> all = nodes.findAll();
            Map<String, TaxonomyNode> byId = byId(all);
            Map<String, Long> childCounts = all.stream()
                    .filter(node -> node.parentId().isPresent())
                    .collect(Collectors.groupingBy(node -> node.parentId().orElseThrow(), Collectors.counting()));
            return all.stream()
                    .map(node -> new NodeSummary(
                            node,
                            EffectiveAttributes.chain(node, byId).size(),
                            childCounts.getOrDefault(node.id(), 0L).intValue()))
                    .toList();
        }
    }

    /**
     * One node with its path, children and effective attributes (FR-007, FR-013 to FR-016).
     *
     * @throws NodeNotFoundException if no node has the system ID
     */
    public NodeDetails find(String systemId) {
        synchronized (lock) {
            return details(existing(systemId, "nodeId"));
        }
    }

    /**
     * Creates a top-level node ({@code parentId} null or blank) or a child of an existing node (FR-001 to FR-004).
     *
     * @throws TaxonomyChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws com.pim.backend.taxonomy.model.InvalidNodeException for an invalid name or description
     * @throws NodeNotFoundException if the parent does not exist
     * @throws NodeNameInUseException when a sibling has the name, ignoring letter case
     */
    public NodeDetails create(String name, String description, String parentId, AttributePermissions permissions) {
        checkPermission(permissions);
        NodeName nodeName = NodeName.of(name);
        TaxonomyNode.normalizedDescription(description);
        String requestedParent = parentId == null || parentId.isBlank() ? null : parentId.strip();
        synchronized (lock) {
            String parentKey = requestedParent == null
                    ? null
                    : existing(requestedParent, "parentId").id();
            if (nodes.existsWithName(parentKey, nodeName, null)) {
                throw new NodeNameInUseException(nodeName);
            }
            AssignmentResult assignment = identifierService.assign(
                    ObjectType.CLASSIFICATION, null, declaredKeyFieldValues(nodeName), NO_MANUAL_IDS);
            TaxonomyNode node = TaxonomyNode.create(
                    assignment.registration().systemId().value(), nodeName, description, parentKey, clock.instant());
            nodes.insert(node);
            return details(node);
        }
    }

    /**
     * Renames the node and replaces its description; ID, parent, children and assignments stay (FR-005).
     *
     * @throws TaxonomyChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws NodeNotFoundException if no node has the system ID
     * @throws NodeNameInUseException when a sibling has the name, ignoring letter case
     */
    public NodeDetails update(String systemId, String name, String description, AttributePermissions permissions) {
        checkPermission(permissions);
        NodeName nodeName = NodeName.of(name);
        TaxonomyNode.normalizedDescription(description);
        synchronized (lock) {
            TaxonomyNode node = existing(systemId, "nodeId");
            if (nodes.existsWithName(node.parentId().orElse(null), nodeName, node.id())) {
                throw new NodeNameInUseException(nodeName);
            }
            TaxonomyNode renamed = node.renamed(nodeName, description, clock.instant());
            nodes.update(renamed);
            return details(renamed);
        }
    }

    /**
     * Deletes a node with no children and no direct attributes (FR-006).
     *
     * @throws TaxonomyChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws NodeNotFoundException if no node has the system ID
     * @throws NodeNotEmptyException if the node has children or direct attributes; nothing changes
     */
    public void delete(String systemId, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            TaxonomyNode node = existing(systemId, "nodeId");
            long children = nodes.findAll().stream()
                    .filter(candidate ->
                            candidate.parentId().filter(node.id()::equals).isPresent())
                    .count();
            if (children > 0) {
                throw NodeNotEmptyException.withChildren(node, (int) children);
            }
            if (!node.attributeIds().isEmpty()) {
                throw NodeNotEmptyException.withAttributes(node);
            }
            nodes.delete(node.id());
        }
    }

    /**
     * Moves a node, with its whole branch, under another node or to the top level (feature 011 FR-001 to FR-009). Only
     * the node's parent and modification date change: its descendants point to it, so they follow without being
     * written, and levels, paths and effective attributes are derived from the new chain on the next read (feature
     * 011 research R-01, R-05). The checks and the single save run under the taxonomy lock, so two moves cannot
     * interleave and create a loop (R-02). No depth limit applies (FR-008).
     *
     * @param parentId the new parent's system ID; {@code null} or blank for the top level (FR-004)
     * @throws TaxonomyChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws NodeNotFoundException if the node (field {@code nodeId}) or the new parent (field {@code parentId}) does
     *     not exist
     * @throws NodeMoveNotAllowedException if the new parent is the node or one of its descendants, or is already its
     *     parent; nothing changes
     * @throws NodeNameInUseException if the destination already has a child with the node's name; nothing changes
     */
    public MoveOutcome move(String systemId, String parentId, AttributePermissions permissions) {
        checkPermission(permissions);
        String requestedParent = parentId == null || parentId.isBlank() ? null : parentId.strip();
        synchronized (lock) {
            TaxonomyNode node = existing(systemId, "nodeId");
            TaxonomyNode destination = requestedParent == null ? null : existing(requestedParent, "parentId");
            Set<String> descendants = descendantIds(node, nodes.findAll());
            if (destination != null && (destination.id().equals(node.id()) || descendants.contains(destination.id()))) {
                throw NodeMoveNotAllowedException.intoOwnBranch(node);
            }
            String destinationId = destination == null ? null : destination.id();
            if (node.parentId().equals(Optional.ofNullable(destinationId))) {
                throw destination == null
                        ? NodeMoveNotAllowedException.alreadyTopLevel(node)
                        : NodeMoveNotAllowedException.alreadyUnder(node, destination);
            }
            if (nodes.existsWithName(destinationId, node.name(), node.id())) {
                throw NodeNameInUseException.atDestination(node.name(), destination);
            }
            TaxonomyNode moved = node.movedTo(destinationId, clock.instant());
            nodes.update(moved);
            return new MoveOutcome(details(moved), 1 + descendants.size());
        }
    }

    /**
     * Assigns existing attributes directly to the node, all or nothing (FR-008, FR-009, FR-011). Attributes already
     * direct are reported, not added again.
     *
     * @throws TaxonomyChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws NodeNotFoundException if no node has the system ID
     * @throws UnknownAttributeException naming the first unknown attribute; nothing is assigned
     */
    public AddAttributesOutcome addAttributes(
            String systemId, List<String> attributeIds, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            TaxonomyNode node = existing(systemId, "nodeId");
            List<Attribute> attributes =
                    attributeIds.stream().map(this::existingAttribute).toList();
            List<String> added = new ArrayList<>();
            List<String> alreadyAssigned = new ArrayList<>();
            for (Attribute attribute : attributes) {
                String attributeId = attribute.systemId();
                if (node.hasDirect(attributeId)) {
                    if (!alreadyAssigned.contains(attributeId)) {
                        alreadyAssigned.add(attributeId);
                    }
                } else if (!added.contains(attributeId)) {
                    added.add(attributeId);
                }
            }
            TaxonomyNode changed = node;
            if (!added.isEmpty()) {
                changed = node.withAttributes(added, clock.instant());
                nodes.update(changed);
            }
            return new AddAttributesOutcome(details(changed), added, alreadyAssigned);
        }
    }

    /**
     * Removes a direct assignment; descendants stop inheriting the attribute from this node (FR-010). The attribute
     * itself is unchanged (FR-011).
     *
     * @throws TaxonomyChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws NodeNotFoundException if no node has the system ID
     * @throws AttributeNotDirectException if the attribute is only inherited here
     * @throws AttributeNotOnNodeException if the attribute neither is assigned to nor inherited by the node
     */
    public void removeAttribute(String systemId, String attributeId, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            TaxonomyNode node = existing(systemId, "nodeId");
            if (node.hasDirect(attributeId)) {
                nodes.update(node.withoutAttribute(attributeId, clock.instant()));
                return;
            }
            String key = TaxonomyNode.key(attributeId);
            Optional<EffectiveAttribute> inherited = EffectiveAttributes.of(node, byId(nodes.findAll())).stream()
                    .filter(entry -> entry.attributeId().equals(key))
                    .findFirst();
            if (inherited.isEmpty()) {
                throw new AttributeNotOnNodeException(attributeId);
            }
            throw new AttributeNotDirectException(
                    attributeName(attributeId), inherited.get().origin());
        }
    }

    /**
     * Runs {@code action} only if no node lists the attribute directly (compared ignoring letter case), while holding
     * the taxonomy lock, so no assignment can be added in between (FR-021; research R-05). The action may take the
     * attribute lock: taxonomy → attribute is the order {@link #addAttributes} uses.
     *
     * @return the nodes listing the attribute, sorted by name ignoring letter case; empty if the action ran
     */
    public List<NodeReference> runIfOnNoNode(String attributeId, Runnable action) {
        synchronized (lock) {
            List<NodeReference> listing = nodes.findAllListing(attributeId).stream()
                    .map(TaxonomyNode::reference)
                    .toList();
            if (listing.isEmpty()) {
                action.run();
            }
            return listing;
        }
    }

    private static void checkPermission(AttributePermissions permissions) {
        if (!permissions.allowsCreation()) {
            throw new TaxonomyChangeNotPermittedException();
        }
    }

    private TaxonomyNode existing(String systemId, String field) {
        return nodes.findById(systemId).orElseThrow(() -> new NodeNotFoundException(systemId, field));
    }

    private Attribute existingAttribute(String attributeId) {
        try {
            return attributeService.find(attributeId);
        } catch (AttributeNotFoundException exception) {
            throw new UnknownAttributeException(attributeId);
        }
    }

    private String attributeName(String attributeId) {
        try {
            return attributeService.find(attributeId).displayName().value();
        } catch (AttributeNotFoundException exception) {
            return attributeId;
        }
    }

    /** The key-field values feature 003 expects for classifications: the name if it declares it, none otherwise. */
    private Map<String, String> declaredKeyFieldValues(NodeName name) {
        boolean declared = identifierService
                .findConfiguration(ObjectType.CLASSIFICATION)
                .keyFields()
                .fieldNames()
                .contains(NAME_KEY_FIELD);
        return declared ? Map.of(NAME_KEY_FIELD, name.value()) : Map.of();
    }

    /**
     * The storage keys of every node below {@code node}, at any depth, walking parent links breadth-first. The visited
     * set stops the walk on a damaged store, as {@link EffectiveAttributes#chain} does.
     */
    private static Set<String> descendantIds(TaxonomyNode node, List<TaxonomyNode> all) {
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (TaxonomyNode candidate : all) {
            candidate
                    .parentId()
                    .ifPresent(parent -> childrenByParent
                            .computeIfAbsent(parent, key -> new ArrayList<>())
                            .add(candidate.id()));
        }
        Set<String> found = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(childrenByParent.getOrDefault(node.id(), List.of()));
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!current.equals(node.id()) && found.add(current)) {
                pending.addAll(childrenByParent.getOrDefault(current, List.of()));
            }
        }
        return found;
    }

    private static Map<String, TaxonomyNode> byId(List<TaxonomyNode> all) {
        Map<String, TaxonomyNode> byId = new HashMap<>();
        all.forEach(node -> byId.put(node.id(), node));
        return byId;
    }

    private NodeDetails details(TaxonomyNode node) {
        List<TaxonomyNode> all = nodes.findAll();
        Map<String, TaxonomyNode> byId = byId(all);
        byId.put(node.id(), node);
        List<TaxonomyNode> chain = EffectiveAttributes.chain(node, byId);
        List<NodeReference> path = chain.subList(0, chain.size() - 1).stream()
                .map(TaxonomyNode::reference)
                .toList();
        List<NodeReference> children = all.stream()
                .filter(candidate ->
                        candidate.parentId().filter(node.id()::equals).isPresent())
                .map(TaxonomyNode::reference)
                .toList();
        Map<String, Attribute> attributes = attributeService.findAll().stream()
                .collect(Collectors.toMap(
                        attribute -> TaxonomyNode.key(attribute.systemId()), Function.identity(), (a, b) -> a));
        List<NodeAttribute> effective = new ArrayList<>();
        for (EffectiveAttribute entry : EffectiveAttributes.of(node, byId)) {
            Attribute attribute = attributes.get(entry.attributeId());
            if (attribute == null) {
                // Not expected: attributes on nodes cannot be deleted (FR-021).
                LOG.warn("Node {} refers to missing attribute {}", node.systemId(), entry.attributeId());
                continue;
            }
            effective.add(new NodeAttribute(
                    entry,
                    attribute.systemId(),
                    attribute.displayName().value(),
                    attribute.dataType().name()));
        }
        return new NodeDetails(node, chain.size(), path, children, effective);
    }
}
