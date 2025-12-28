package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.ConnectionConfig;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

public class MQConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(MQConnectionManager.class);
    private MQQueueManager queueManager;
    private ConnectionConfig currentConfig;
    private boolean connected = false;

    public void connect(ConnectionConfig config) throws MQException {
        if (connected) {
            disconnect();
        }

        logger.info("Connecting to queue manager: {} at {}:{}",
                   config.getQueueManager(), config.getHost(), config.getPort());

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
            queueManager = new MQQueueManager(config.getQueueManager(), properties);
            currentConfig = config;
            connected = true;
            logger.info("Successfully connected to queue manager: {}", config.getQueueManager());
        } catch (MQException e) {
            logger.error("Failed to connect to queue manager. Reason code: {} ({})",
                        e.getReason(), getMQErrorDescription(e.getReason()));
            logger.error("Connection details - Host: {}, Port: {}, Channel: {}, QM: {}",
                        config.getHost(), config.getPort(), config.getChannel(), config.getQueueManager());
            throw new MQException(e.getCompCode(), e.getReason(),
                                 formatMQError(e, config));
        }
    }

    private String formatMQError(MQException e, ConnectionConfig config) {
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

    public void disconnect() {
        if (queueManager != null) {
            try {
                if (queueManager.isConnected()) {
                    queueManager.disconnect();
                    logger.info("Disconnected from queue manager: {}",
                               currentConfig != null ? currentConfig.getQueueManager() : "unknown");
                }
            } catch (MQException e) {
                logger.error("Error disconnecting from queue manager", e);
            } finally {
                queueManager = null;
                currentConfig = null;
                connected = false;
            }
        }
    }

    public boolean isConnected() {
        if (queueManager == null) {
            return false;
        }
        return queueManager.isConnected();
    }

    public MQQueueManager getQueueManager() {
        if (!connected || queueManager == null) {
            throw new IllegalStateException("Not connected to queue manager");
        }
        return queueManager;
    }

    public ConnectionConfig getCurrentConfig() {
        return currentConfig;
    }

    public void testConnection(ConnectionConfig config) throws MQException {
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
        if (!connected || currentConfig == null) {
            return "Not connected";
        }
        return String.format("Connected to %s at %s:%d",
            currentConfig.getQueueManager(),
            currentConfig.getHost(),
            currentConfig.getPort());
    }
}
