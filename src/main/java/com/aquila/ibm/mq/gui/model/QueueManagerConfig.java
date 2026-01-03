package com.aquila.ibm.mq.gui.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.beans.Transient;

@Setter
@Getter
@ToString
public class QueueManagerConfig {
    private String name;
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

    public QueueManagerConfig(String name, String host, int port, String channel,
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
        return String.format("%s(%d)", host, port);
    }
}
