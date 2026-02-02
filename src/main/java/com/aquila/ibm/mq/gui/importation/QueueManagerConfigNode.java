/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Configuration node for queue manager connections in import files.
 * Contains connection parameters: name, host, port, channel,
 * credentials, and SSL settings for JSON deserialization.
 */
package com.aquila.ibm.mq.gui.importation;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class QueueManagerConfigNode {
    private String name;
    private String host;
    private int port;
    private String channel;
    private String queueManager;
    private String username;
    private String password;
    private boolean sslEnabled;

    public QueueManagerConfigNode() {
        this.port = 1414;
        this.sslEnabled = false;
    }

    public QueueManagerConfigNode(String name, String host, int port, String channel,
                                  String queueManager, String username, String password) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.channel = channel;
        this.queueManager = queueManager;
        this.username = username;
        this.password = password;
        this.sslEnabled = false;
    }

    public String getLabel() {
        return String.format("%s(%d)", host, (Integer)port);
    }
}
