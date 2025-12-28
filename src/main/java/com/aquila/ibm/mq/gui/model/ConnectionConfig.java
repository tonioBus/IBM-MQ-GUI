package com.aquila.ibm.mq.gui.model;

public class ConnectionConfig {
    private String name;
    private String host;
    private int port;
    private String channel;
    private String queueManager;
    private String username;
    private String password;
    private boolean sslEnabled;

    public ConnectionConfig() {
        this.port = 1414;
        this.sslEnabled = false;
    }

    public ConnectionConfig(String name, String host, int port, String channel,
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getQueueManager() {
        return queueManager;
    }

    public void setQueueManager(String queueManager) {
        this.queueManager = queueManager;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    @Override
    public String toString() {
        return name != null ? name : queueManager + "@" + host;
    }
}
