package com.aquila.ibm.mq.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a node in the queue manager hierarchy tree.
 * Can be either a folder or a queue manager reference.
 */
public class HierarchyNode {
    private String id;
    private NodeType type;
    private String name;
    private String connectionConfigId;  // Reference to ConnectionConfig name (null for folders)
    private String parentId;            // Parent node ID (null for root nodes)
    private List<String> childIds;
    private boolean expanded;           // Tree expansion state

    public enum NodeType {
        FOLDER,
        QUEUE_MANAGER
    }

    public HierarchyNode() {
        this.id = UUID.randomUUID().toString();
        this.childIds = new ArrayList<>();
        this.expanded = false;
    }

    public HierarchyNode(NodeType type, String name) {
        this();
        this.type = type;
        this.name = name;
    }

    public HierarchyNode(NodeType type, String name, String connectionConfigId) {
        this(type, name);
        this.connectionConfigId = connectionConfigId;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConnectionConfigId() {
        return connectionConfigId;
    }

    public void setConnectionConfigId(String connectionConfigId) {
        this.connectionConfigId = connectionConfigId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public List<String> getChildIds() {
        return childIds;
    }

    public void setChildIds(List<String> childIds) {
        this.childIds = childIds;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    // Utility methods

    public boolean isFolder() {
        return type == NodeType.FOLDER;
    }

    public boolean isQueueManager() {
        return type == NodeType.QUEUE_MANAGER;
    }

    public void addChild(String childId) {
        if (!childIds.contains(childId)) {
            childIds.add(childId);
        }
    }

    public void removeChild(String childId) {
        childIds.remove(childId);
    }

    @Override
    public String toString() {
        return "HierarchyNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", connectionConfigId='" + connectionConfigId + '\'' +
                ", parentId='" + parentId + '\'' +
                ", childCount=" + childIds.size() +
                ", expanded=" + expanded +
                '}';
    }
}
