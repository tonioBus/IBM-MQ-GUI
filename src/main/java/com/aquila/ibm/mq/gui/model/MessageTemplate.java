package com.aquila.ibm.mq.gui.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MessageTemplate {
    private String name;
    private String content;
    private int priority;
    private int persistence;
    private int messageCount;
    private int delayMs;

    public MessageTemplate(String name, String content, int priority, int persistence, int messageCount, int delayMs) {
        this.name = name;
        this.content = content;
        this.priority = priority;
        this.persistence = persistence;
        this.messageCount = messageCount;
        this.delayMs = delayMs;
    }
}
