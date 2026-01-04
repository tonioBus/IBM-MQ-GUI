package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.QueueManagerConfig;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

public class MQConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(MQConnectionManager.class);

    // Multi-connection support
    private Map<String, MQQueueManager> activeConnections;
    private Map<String, QueueManagerConfig> connectionConfigs;
    private String activeConnectionId;

    // Legacy fields (deprecated but maintained for backward compatibility)
    private MQQueueManager queueManager;
    private QueueManagerConfig currentConfig;
    private boolean connected = false;

    public MQConnectionManager() {
        this.activeConnections = new HashMap<>();
        this.connectionConfigs = new HashMap<>();
    }

    /**
     * Legacy connect method for backward compatibility.
     * Connects using the connection's name as the ID and sets it as active.
     */
    public void connect(QueueManagerConfig config) throws MQException {
        String connectionId = getConnectionId(config);
        connect(connectionId, config);
        setActiveConnection(connectionId);
    }

    /**
     * Connect to a queue manager with a specific connection ID.
     * Allows multiple simultaneous connections.
     * @param connectionId Unique identifier for this connection
     * @param config Connection configuration
     */
    public void connect(String connectionId, QueueManagerConfig config) throws MQException {
        if (connectionId == null || connectionId.isEmpty()) {
            throw new IllegalArgumentException("Connection ID cannot be null or empty");
        }

        if (isConnected(connectionId)) {
            logger.info("Already connected to {}, reusing existing connection", connectionId);
            return;
        }

        logger.info("Connecting to queue manager: {} at {}:{} with ID: {}",
                   config.getQueueManager(), config.getHost(), config.getPort(), connectionId);

        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(MQConstants.HOST_NAME_PROPERTY, config.getHost());
        properties.put(MQConstants.PORT_PROPERTY, config.getPort());
        properties.put(MQConstants.CHANNEL_PROPERTY, config.getChannel());
        properties.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES_CLIENT);

        // Enable MQCSP authentication for compatibility
        properties.put(MQConstants.USE_MQCSP_AUTHENTICATION_PROPERTY, true);

        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            properties.put(MQConstants.USER_ID_PROPERTY, config.getUsername());
            properties.put(MQConstants.PASSWORD_PROPERTY, config.getPassword());
            logger.info("Using credentials for user: {}", config.getUsername());
        } else {
            logger.info("Connecting without credentials");
        }

        try {
            MQQueueManager qm = new MQQueueManager(config.getQueueManager(), properties);
            activeConnections.put(connectionId, qm);
            connectionConfigs.put(connectionId, config);

            // Update legacy fields for backward compatibility
            if (activeConnectionId == null || connectionId.equals(activeConnectionId)) {
                queueManager = qm;
                currentConfig = config;
                connected = true;
            }

            logger.info("Successfully connected to queue manager: {} (ID: {})", config.getQueueManager(), connectionId);
        } catch (MQException e) {
            logger.error("Failed to connect to queue manager. Reason code: {} ({})",
                        e.getReason(), getMQErrorDescription(e.getReason()));
            logger.error("Connection details - Host: {}, Port: {}, Channel: {}, QM: {}",
                        config.getHost(), config.getPort(), config.getChannel(), config.getQueueManager());
            throw new MQException(e.getCompCode(), e.getReason(),
                                 formatMQError(e, config));
        }
    }

    /**
     * Get a stable connection ID from a ConnectionConfig.
     */
    private String getConnectionId(QueueManagerConfig config) {
        return config.getName() != null && !config.getName().isEmpty()
            ? config.getName()
            : config.getQueueManager() + "@" + config.getHost();
    }

    private String formatMQError(MQException e, QueueManagerConfig config) {
        StringBuilder msg = new StringBuilder();
        msg.append(getMQErrorDescription(e.getReason())).append("\n\n");
        msg.append("Connection Details:\n");
        msg.append("  Queue Manager: ").append(config.getQueueManager()).append("\n");
        msg.append("  Host: ").append(config.getHost()).append("\n");
        msg.append("  Port: ").append(config.getPort()).append("\n");
        msg.append("  Channel: ").append(config.getChannel()).append("\n\n");
        msg.append(getTroubleshootingTips(e.getReason()));
        return msg.toString();
    }

    private String getMQErrorDescription(int reasonCode) {
        switch (reasonCode) {
            case 2009:
                return "MQRC_CONNECTION_BROKEN (2009): Connection to queue manager broken or failed";
            case 2059:
                return "MQRC_Q_MGR_NOT_AVAILABLE (2059): Queue manager not available";
            case 2538:
                return "MQRC_HOST_NOT_AVAILABLE (2538): Host not available or name cannot be resolved";
            case 2393:
                return "MQRC_NOT_AUTHORIZED (2393): Not authorized to connect";
            case 2035:
                return "MQRC_NOT_AUTHORIZED (2035): Not authorized (invalid credentials)";
            case 2540:
                return "MQRC_CHANNEL_NOT_AVAILABLE (2540): Channel not available";
            default:
                return "MQ Error " + reasonCode + ": " + MQConstants.lookupReasonCode(reasonCode);
        }
    }

    private String getTroubleshootingTips(int reasonCode) {
        StringBuilder tips = new StringBuilder("Troubleshooting Tips:\n");

        switch (reasonCode) {
            case 2009:
                tips.append("• Verify the queue manager is running\n");
                tips.append("• Check if the listener is started on the specified port\n");
                tips.append("• Verify the channel name is correct and running\n");
                tips.append("• Check firewall rules between client and server\n");
                tips.append("• Ensure the channel type is SVRCONN (server connection)\n");
                tips.append("• Check CHLAUTH rules: runmqsc <QM> and 'DISPLAY CHLAUTH(*)'\n");
                tips.append("• For Docker/local: try channel 'DEV.APP.SVRCONN' on port 1414\n");
                break;
            case 2059:
                tips.append("• Verify the queue manager name is correct\n");
                tips.append("• Check if the queue manager is running: dspmq\n");
                tips.append("• Ensure listener is started: runmqsc <QM> and 'DISPLAY LISTENER(*)'\n");
                break;
            case 2538:
                tips.append("• Verify the hostname/IP address is correct\n");
                tips.append("• Check DNS resolution or try using IP address directly\n");
                tips.append("• Verify network connectivity: ping <hostname>\n");
                tips.append("• Check if port is reachable: telnet <hostname> <port>\n");
                break;
            case 2393:
            case 2035:
                tips.append("• Verify username and password are correct\n");
                tips.append("• Check channel authentication rules (CHLAUTH)\n");
                tips.append("• Verify connection authentication (CONNAUTH) settings\n");
                tips.append("• For dev environments, you may need to disable CHLAUTH:\n");
                tips.append("  ALTER QMGR CHLAUTH(DISABLED)\n");
                break;
            case 2540:
                tips.append("• Verify the channel exists and is defined\n");
                tips.append("• Check if channel is running: runmqsc <QM> and 'DISPLAY CHANNEL(<name>)'\n");
                tips.append("• Ensure channel type is SVRCONN\n");
                break;
            default:
                tips.append("• Check queue manager error logs\n");
                tips.append("• Review AMQERR01.LOG for detailed errors\n");
                tips.append("• Enable trace for detailed diagnostics if needed\n");
                break;
        }

        return tips.toString();
    }

    /**
     * Legacy disconnect method - disconnects the active connection.
     */
    public void disconnect() {
        if (activeConnectionId != null) {
            disconnect(activeConnectionId);
            activeConnectionId = null;
        }

        // Legacy cleanup
        queueManager = null;
        currentConfig = null;
        connected = false;
    }

    /**
     * Disconnect a specific queue manager connection.
     * @param connectionId The connection ID to disconnect
     */
    public void disconnect(String connectionId) {
        if (connectionId == null) {
            return;
        }

        MQQueueManager qm = activeConnections.get(connectionId);
        if (qm != null) {
            try {
                if (qm.isConnected()) {
                    qm.disconnect();
                    QueueManagerConfig config = connectionConfigs.get(connectionId);
                    logger.info("Disconnected from queue manager: {} (ID: {})",
                               config != null ? config.getQueueManager() : "unknown",
                               connectionId);
                }
            } catch (MQException e) {
                logger.error("Error disconnecting from queue manager (ID: {})", connectionId, e);
            } finally {
                activeConnections.remove(connectionId);
                connectionConfigs.remove(connectionId);

                // Update legacy fields if this was the active connection
                if (connectionId.equals(activeConnectionId)) {
                    queueManager = null;
                    currentConfig = null;
                    connected = false;
                    activeConnectionId = null;
                }
            }
        }
    }

    /**
     * Disconnect all active connections.
     */
    public void disconnectAll() {
        logger.info("Disconnecting all {} active connections", activeConnections.size());

        // Copy key set to avoid concurrent modification
        Set<String> connectionIds = Set.copyOf(activeConnections.keySet());
        for (String connectionId : connectionIds) {
            disconnect(connectionId);
        }

        // Ensure legacy fields are cleared
        queueManager = null;
        currentConfig = null;
        connected = false;
        activeConnectionId = null;
    }

    /**
     * Legacy isConnected method - checks if there's an active connection.
     */
    public boolean isConnected() {
        if (activeConnectionId != null) {
            return isConnected(activeConnectionId);
        }
        // Fallback to legacy field
        if (queueManager == null) {
            return false;
        }
        return queueManager.isConnected();
    }

    /**
     * Check if a specific connection is active.
     * @param connectionId The connection ID to check
     * @return true if connected, false otherwise
     */
    public boolean isConnected(String connectionId) {
        MQQueueManager qm = activeConnections.get(connectionId);
        return qm != null && qm.isConnected();
    }

    /**
     * Set the active connection for operations.
     * @param connectionId The connection ID to make active
     */
    public void setActiveConnection(String connectionId) {
        if (connectionId != null && !isConnected(connectionId)) {
            logger.warn("Cannot set active connection to {}: not connected", connectionId);
            return;
        }

        this.activeConnectionId = connectionId;

        // Update legacy fields
        if (connectionId != null) {
            this.queueManager = activeConnections.get(connectionId);
            this.currentConfig = connectionConfigs.get(connectionId);
            this.connected = true;
        } else {
            this.queueManager = null;
            this.currentConfig = null;
            this.connected = false;
        }

        logger.debug("Active connection set to: {}", connectionId);
    }

    /**
     * Legacy getQueueManager - returns the active queue manager.
     */
    public MQQueueManager getQueueManager() {
        if (activeConnectionId != null) {
            return getQueueManager(activeConnectionId);
        }
        // Fallback to legacy field
        if (!connected || queueManager == null) {
            throw new IllegalStateException("Not connected to queue manager");
        }
        return queueManager;
    }

    /**
     * Get a specific queue manager by connection ID.
     * @param connectionId The connection ID
     * @return The MQQueueManager instance
     */
    public MQQueueManager getQueueManager(String connectionId) {
        if (!isConnected(connectionId)) {
            throw new IllegalStateException("Not connected to queue manager: " + connectionId);
        }
        return activeConnections.get(connectionId);
    }

    /**
     * Get the currently active queue manager.
     * @return The active MQQueueManager, or null if none is active
     */
    public MQQueueManager getActiveQueueManager() {
        if (activeConnectionId == null) {
            return null;
        }
        return activeConnections.get(activeConnectionId);
    }

    /**
     * Get all connected connection IDs.
     * @return Set of connection IDs
     */
    public Set<String> getConnectedIds() {
        return Set.copyOf(activeConnections.keySet());
    }

    /**
     * Get the active connection ID.
     * @return The active connection ID, or null if none is active
     */
    public String getActiveConnectionId() {
        return activeConnectionId;
    }

    /**
     * Legacy getCurrentConfig - returns the active connection's config.
     */
    public QueueManagerConfig getCurrentConfig() {
        if (activeConnectionId != null) {
            return connectionConfigs.get(activeConnectionId);
        }
        return currentConfig;
    }

    /**
     * Get the configuration for a specific connection.
     * @param connectionId The connection ID
     * @return The ConnectionConfig, or null if not found
     */
    public QueueManagerConfig getConfig(String connectionId) {
        return connectionConfigs.get(connectionId);
    }

    /**
     * Get the configuration for the active connection.
     * @return The active ConnectionConfig, or null if none is active
     */
    public QueueManagerConfig getActiveConfig() {
        return activeConnectionId != null ? connectionConfigs.get(activeConnectionId) : null;
    }

    public void testConnection(QueueManagerConfig config) throws MQException {
        logger.info("Testing connection to: {}", config.getQueueManager());
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(MQConstants.HOST_NAME_PROPERTY, config.getHost());
        properties.put(MQConstants.PORT_PROPERTY, config.getPort());
        properties.put(MQConstants.CHANNEL_PROPERTY, config.getChannel());
        properties.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES_CLIENT);
        properties.put(MQConstants.USE_MQCSP_AUTHENTICATION_PROPERTY, true);

        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            properties.put(MQConstants.USER_ID_PROPERTY, config.getUsername());
            properties.put(MQConstants.PASSWORD_PROPERTY, config.getPassword());
        }

        MQQueueManager testQM = null;
        try {
            testQM = new MQQueueManager(config.getQueueManager(), properties);
            logger.info("Connection test successful");
        } catch (MQException e) {
            logger.error("Connection test failed. Reason code: {} ({})",
                        e.getReason(), getMQErrorDescription(e.getReason()));
            throw new MQException(e.getCompCode(), e.getReason(),
                                 formatMQError(e, config));
        } finally {
            if (testQM != null && testQM.isConnected()) {
                testQM.disconnect();
            }
        }
    }

    public String getConnectionStatus() {
        if (activeConnectionId != null) {
            QueueManagerConfig config = getActiveConfig();
            if (config != null) {
                return String.format("Connected to %s at %s:%d",
                    config.getQueueManager(),
                    config.getHost(),
                    config.getPort());
            }
        }

        // Show count of active connections
        int count = activeConnections.size();
        if (count == 0) {
            return "Not connected";
        } else if (count == 1) {
            return "1 connection active";
        } else {
            return String.format("%d connections active", count);
        }
    }
}
