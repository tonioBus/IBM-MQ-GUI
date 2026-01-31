/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Data model representing an IBM MQ message.
 * Contains message metadata (ID, correlation ID, priority, persistence)
 * and message content with support for various encodings.
 */
package com.aquila.ibm.mq.gui.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class MessageInfo {
    private byte[] messageId;
    private byte[] correlationId;
    private String messageData;
    private byte[] messageBytes;
    private LocalDateTime timestamp;
    private int format;
    private int priority;
    private int persistence;
    private int encoding;
    private int characterSet;
    private long messageLength;
    private Map<String, Object> properties;

    public MessageInfo() {
        this.properties = new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }

    public String getMessageIdAsHex() {
        if (messageId == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : messageId) {
            sb.append(String.format("%02X", (Byte)b));
        }
        return sb.toString();
    }

    public String getCorrelationIdAsHex() {
        if (correlationId == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : correlationId) {
            sb.append(String.format("%02X", (Byte)b));
        }
        return sb.toString();
    }

    public void setProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    public Object getProperty(String key) {
        return this.properties.get(key);
    }

    @Override
    public String toString() {
        return "Message[" + getMessageIdAsHex() + "] - " +
               (messageData != null ? messageData.substring(0, Math.min(50, messageData.length())) : messageLength + " bytes");
    }
}
