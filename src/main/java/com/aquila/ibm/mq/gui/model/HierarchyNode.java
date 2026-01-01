package com.aquila.ibm.mq.gui.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a node in the queue manager hierarchy tree.
 * Can be either a folder or a queue manager reference.
 */
@Getter
@Setter
@ToString
public class HierarchyNode {

    public enum NodeType {
        FOLDER,
        BROWSER
    }

    private String id;
    private QueueBrowserConfig queueBrowserConfig;
    private NodeType type;
    private String name;
    private String connectionConfigId;  // Reference to ConnectionConfig name (null for folders)
    private String parentId;            // Parent node ID (null for root nodes)
    private List<String> childIds;
    private boolean expanded;           // Tree expansion state

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

    // Utility methods

    public boolean isFolder() {
        return type == NodeType.FOLDER;
    }

    public boolean isQueueBrowser() {
        return type == NodeType.BROWSER;
    }

    public void addChild(String childId) {
        if (!childIds.contains(childId)) {
            childIds.add(childId);
        }
    }

    public void removeChild(String childId) {
        childIds.remove(childId);
    }

}
