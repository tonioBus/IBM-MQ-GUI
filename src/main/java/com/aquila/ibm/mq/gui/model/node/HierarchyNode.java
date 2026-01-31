/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Represents a node in the queue manager hierarchy tree.
 * Can be either a folder (for organization) or a queue browser reference.
 * Supports parent-child relationships and tree expansion state.
 */
package com.aquila.ibm.mq.gui.model.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
        QUEUE
    }

    private String id;
    private NodeType type;
    private String name;
    private String parentId;            // Parent node ID (null for root nodes)
    private List<String> childIds;
    private boolean expanded;           // Tree expansion state

    @JsonIgnore
    private transient QueueNode queueNode;

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

    @JsonIgnore
    public boolean isFolder() {
        return type == NodeType.FOLDER;
    }

    @JsonIgnore
    public boolean isQueue() {
        return type == NodeType.QUEUE;
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
