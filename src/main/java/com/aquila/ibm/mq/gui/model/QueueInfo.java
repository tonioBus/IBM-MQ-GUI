package com.aquila.ibm.mq.gui.model;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Builder
@AllArgsConstructor
@Setter
@Getter
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

    public QueueInfo(String queue, String label) {
        this.label = label;
        this(queue);
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
