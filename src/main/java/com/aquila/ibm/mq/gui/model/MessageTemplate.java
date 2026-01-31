/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Data model for reusable message templates.
 * Stores template name, content, priority, persistence,
 * and batch sending configuration.
 */
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
