package com.aquila.ibm.mq.gui.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Container for the entire hierarchy structure of queue managers and folders.
 * Maintains a flat map of all nodes with references to root nodes.
 */
@ToString
@Getter
public class HierarchyConfig {
    private static final Logger logger = LoggerFactory.getLogger(HierarchyConfig.class);

    private Map<String, HierarchyNode> nodes;
    private List<String> rootNodeIds;
    @Setter
    private String selectedNodeId;

    public HierarchyConfig() {
        this.nodes = new HashMap<>();
        this.rootNodeIds = new ArrayList<>();
    }

    /**
     * Get a node by its ID.
     */
    public HierarchyNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * Add a new node to the hierarchy.
     * @param node The node to add
     * @param parentId The parent node ID (null for root level)
     */
    public void addNode(HierarchyNode node, String parentId) {
        if (node == null) {
            logger.warn("Attempted to add null node");
            return;
        }

        nodes.put(node.getId(), node);
        node.setParentId(parentId);

        if (parentId == null) {
            // Add to root level
            if (!rootNodeIds.contains(node.getId())) {
                rootNodeIds.add(node.getId());
            }
        } else {
            // Add to parent's children
            HierarchyNode parent = nodes.get(parentId);
            if (parent != null) {
                parent.addChild(node.getId());
            } else {
                logger.warn("Parent node not found: {}, adding to root instead", parentId);
                if (!rootNodeIds.contains(node.getId())) {
                    rootNodeIds.add(node.getId());
                }
                node.setParentId(null);
            }
        }

        logger.debug("Added node: {} to parent: {}", node.getName(), parentId);
    }

    /**
     * Remove a node and all its descendants from the hierarchy.
     * @param nodeId The ID of the node to remove
     */
    public void removeNode(String nodeId) {
        HierarchyNode node = nodes.get(nodeId);
        if (node == null) {
            logger.warn("Node not found for removal: {}", nodeId);
            return;
        }

        // Recursively remove all children first
        List<String> childrenCopy = new ArrayList<>(node.getChildIds());
        for (String childId : childrenCopy) {
            removeNode(childId);
        }

        // Remove from parent's children list
        if (node.getParentId() != null) {
            HierarchyNode parent = nodes.get(node.getParentId());
            if (parent != null) {
                parent.removeChild(nodeId);
            }
        } else {
            // Remove from root
            rootNodeIds.remove(nodeId);
        }

        // Remove from nodes map
        nodes.remove(nodeId);

        // Clear selection if this node was selected
        if (nodeId.equals(selectedNodeId)) {
            selectedNodeId = null;
        }

        logger.debug("Removed node: {}", node.getName());
    }

    /**
     * Move a node to a new parent.
     * @param nodeId The ID of the node to move
     * @param newParentId The new parent ID (null for root level)
     * @return true if the move was successful, false if it would create a circular dependency
     */
    public boolean moveNode(String nodeId, String newParentId) {
        HierarchyNode node = nodes.get(nodeId);
        if (node == null) {
            logger.warn("Node not found for move: {}", nodeId);
            return false;
        }

        // Prevent moving to self
        if (nodeId.equals(newParentId)) {
            logger.warn("Cannot move node to itself");
            return false;
        }

        // Prevent circular dependencies (can't move folder into its own descendant)
        if (newParentId != null && isDescendant(newParentId, nodeId)) {
            logger.warn("Cannot move node {} to its own descendant {}", nodeId, newParentId);
            return false;
        }

        // Remove from current parent
        if (node.getParentId() != null) {
            HierarchyNode oldParent = nodes.get(node.getParentId());
            if (oldParent != null) {
                oldParent.removeChild(nodeId);
            }
        } else {
            rootNodeIds.remove(nodeId);
        }

        // Add to new parent
        node.setParentId(newParentId);
        if (newParentId == null) {
            if (!rootNodeIds.contains(nodeId)) {
                rootNodeIds.add(nodeId);
            }
        } else {
            HierarchyNode newParent = nodes.get(newParentId);
            if (newParent != null) {
                newParent.addChild(nodeId);
            } else {
                logger.warn("New parent not found: {}, moving to root instead", newParentId);
                if (!rootNodeIds.contains(nodeId)) {
                    rootNodeIds.add(nodeId);
                }
                node.setParentId(null);
            }
        }

        logger.debug("Moved node: {} to new parent: {}", node.getName(), newParentId);
        return true;
    }

    /**
     * Check if a node is a descendant of another node.
     * @param potentialDescendantId The potential descendant ID
     * @param ancestorId The ancestor ID
     * @return true if potentialDescendantId is a descendant of ancestorId
     */
    private boolean isDescendant(String potentialDescendantId, String ancestorId) {
        HierarchyNode node = nodes.get(potentialDescendantId);
        while (node != null && node.getParentId() != null) {
            if (node.getParentId().equals(ancestorId)) {
                return true;
            }
            node = nodes.get(node.getParentId());
        }
        return false;
    }

    /**
     * Get all child nodes of a parent.
     * @param parentId The parent ID (null for root nodes)
     * @return List of child nodes
     */
    public List<HierarchyNode> getChildren(String parentId) {
        List<HierarchyNode> children = new ArrayList<>();

        if (parentId == null) {
            // Get root nodes
            for (String rootId : rootNodeIds) {
                HierarchyNode node = nodes.get(rootId);
                if (node != null) {
                    children.add(node);
                }
            }
        } else {
            // Get children of specific node
            HierarchyNode parent = nodes.get(parentId);
            if (parent != null) {
                for (String childId : parent.getChildIds()) {
                    HierarchyNode child = nodes.get(childId);
                    if (child != null) {
                        children.add(child);
                    }
                }
            }
        }

        return children;
    }

    /**
     * Get all queue manager nodes (not folders).
     */
    public List<HierarchyNode> getAllQueueManagers() {
        List<HierarchyNode> queueManagers = new ArrayList<>();
        for (HierarchyNode node : nodes.values()) {
            if (node.isQueueBrowser()) {
                queueManagers.add(node);
            }
        }
        return queueManagers;
    }

}
