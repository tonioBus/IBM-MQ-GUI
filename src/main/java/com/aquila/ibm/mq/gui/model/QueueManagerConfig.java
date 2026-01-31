/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Configuration model for IBM MQ queue manager connections.
 * Stores connection parameters: host, port, channel, credentials,
 * and SSL settings.
 */
package com.aquila.ibm.mq.gui.model;

import com.aquila.ibm.mq.gui.importation.QueueManagerConfigNode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@EqualsAndHashCode
public class QueueManagerConfig {
    private String host;
    private int port;
    private String channel;
    private String queueManager;
    private String username;
    private String password;
    private boolean sslEnabled;

    public QueueManagerConfig() {
        this.port = 1414;
        this.sslEnabled = false;
    }

    public QueueManagerConfig(String host, int port, String channel,
                              String queueManager, String username, String password) {
        this.host = host;
        this.port = port;
        this.channel = channel;
        this.queueManager = queueManager;
        this.username = username;
        this.password = password;
        this.sslEnabled = false;
    }

    public QueueManagerConfig(QueueManagerConfigNode queueManagerConfigNode) {
        this(
                queueManagerConfigNode.getHost(),
                queueManagerConfigNode.getPort(),
                queueManagerConfigNode.getChannel(),
                queueManagerConfigNode.getQueueManager(),
                queueManagerConfigNode.getUsername(),
                queueManagerConfigNode.getPassword());
    }

    @Override
    public String toString() {
        return String.format("%s@%s(%d)", getQueueManager(), getHost(), getPort());
    }

}
