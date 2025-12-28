package com.aquila.ibm.mq.gui.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

    public byte[] getMessageId() {
        return messageId;
    }

    public void setMessageId(byte[] messageId) {
        this.messageId = messageId;
    }

    public String getMessageIdAsHex() {
        if (messageId == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : messageId) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public byte[] getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(byte[] correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationIdAsHex() {
        if (correlationId == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : correlationId) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public String getMessageData() {
        return messageData;
    }

    public void setMessageData(String messageData) {
        this.messageData = messageData;
    }

    public byte[] getMessageBytes() {
        return messageBytes;
    }

    public void setMessageBytes(byte[] messageBytes) {
        this.messageBytes = messageBytes;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getFormat() {
        return format;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getPersistence() {
        return persistence;
    }

    public void setPersistence(int persistence) {
        this.persistence = persistence;
    }

    public int getEncoding() {
        return encoding;
    }

    public void setEncoding(int encoding) {
        this.encoding = encoding;
    }

    public int getCharacterSet() {
        return characterSet;
    }

    public void setCharacterSet(int characterSet) {
        this.characterSet = characterSet;
    }

    public long getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(long messageLength) {
        this.messageLength = messageLength;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
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
