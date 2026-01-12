package com.aquila.ibm.mq.gui.model;

import com.aquila.ibm.mq.gui.model.HierarchyNode.NodeType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a node in the import/export hierarchy structure.
 * This matches the nested structure used in doc/import.json.
 * Unlike HierarchyNode which uses ID references, this uses nested maps.
 */
@Getter
@Setter
@ToString
@Slf4j
@Deprecated
public class HierarchyImportNode {
    private String name;
    private NodeType type;
    private Map<String, Object> children;
    private String queueMgr;

    public HierarchyImportNode() {
    }

    public HierarchyImportNode(String name, NodeType type) {
        this.name = name;
        this.type = type;
        this.children = new HashMap<>();
    }

    /**
     * Create a HierarchyImportNode from a map (typically from JSON deserialization).
     *
     * @param name The name of the node
     * @param nodeMap The map containing node properties
     * @return HierarchyImportNode instance
     */
    public static HierarchyImportNode fromMap(String name, Map<String, Object> nodeMap) {
        HierarchyImportNode node = new HierarchyImportNode();
        node.setName(name);

        // Extract type
        if (nodeMap.containsKey("type")) {
            String typeStr = (String) nodeMap.get("type");
            node.setType(NodeType.valueOf(typeStr));
            if(node.getType()==NodeType.QUEUE && !nodeMap.containsKey("queueMgr")) {
                log.error("node of type NodeType.QUEUE should have a field \"queueMgr\"");
                return null;
            }
        }

        if( nodeMap.containsKey("queueMgr")) {
            String queueMgr = (String) nodeMap.get("queueMgr");
            node.setQueueMgr(queueMgr);
        }

        // Extract children
        if (nodeMap.containsKey("children")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> childrenMap = (Map<String, Object>) nodeMap.get("children");
            node.setChildren(childrenMap);
        }

        return node;
    }

    /**
     * Convert this node to a map representation (for JSON serialization).
     *
     * @return Map representation of this node
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();

        if (type != null) {
            map.put("type", type.toString());
        }

        if (queueMgr != null) {
            map.put("queueMgr", queueMgr);
        }

        if (children != null && !children.isEmpty()) {
            map.put("children", children);
        }

        return map;
    }

    /**
     * Add a child node to this node.
     *
     * @param childName The name of the child
     * @param child The child node or value (can be HierarchyImportNode, Map, or String)
     */
    public void addChild(String childName, Object child) {
        if (children == null) {
            children = new HashMap<>();
        }
        children.put(childName, child);
    }

    /**
     * Add a child node to this node.
     *
     * @param childName The name of the child
     * @param childNode The child node
     */
    public void addChild(String childName, HierarchyImportNode childNode) {
        if (children == null) {
            children = new HashMap<>();
        }
        children.put(childName, childNode.toMap());
    }

    /**
     * Check if this node has children.
     *
     * @return true if children exist and not empty
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}