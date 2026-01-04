package com.aquila.ibm.mq.gui.model;

import java.util.HashMap;
import java.util.Map;

public class QueueInfo {
    private String queue;
    private String label;
    private int queueType;
    private int currentDepth;
    private int maxDepth;
    private int openInputCount;
    private int openOutputCount;
    private String description;
    private Map<String, Object> attributes;

    public QueueInfo() {
        this.attributes = new HashMap<>();
    }

    public QueueInfo(String queue) {
        this.queue = queue;
        this.attributes = new HashMap<>();
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getQueueType() {
        return queueType;
    }

    public void setQueueType(int queueType) {
        this.queueType = queueType;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public void setCurrentDepth(int currentDepth) {
        this.currentDepth = currentDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getOpenInputCount() {
        return openInputCount;
    }

    public void setOpenInputCount(int openInputCount) {
        this.openInputCount = openInputCount;
    }

    public int getOpenOutputCount() {
        return openOutputCount;
    }

    public void setOpenOutputCount(int openOutputCount) {
        this.openOutputCount = openOutputCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    public double getDepthPercentage() {
        if (maxDepth == 0) return 0;
        return (currentDepth * 100.0) / maxDepth;
    }

    @Override
    public String toString() {
        return queue + " (" + currentDepth + "/" + maxDepth + ")";
    }
}
