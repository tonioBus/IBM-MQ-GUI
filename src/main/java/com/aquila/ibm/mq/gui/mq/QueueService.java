package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private final MQConnectionManager connectionManager;

    public QueueService(MQConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Get all queues for the active connection.
     */
    public List<QueueInfo> getAllQueues() throws MQException, IOException {
        return getAllQueues(false);
    }

    /**
     * Get all queues for the active connection.
     */
    public List<QueueInfo> getAllQueues(boolean includeSystemQueues) throws MQException, IOException {
        MQQueueManager qm = connectionManager.getQueueManager();
        return getAllQueuesForManager(qm, includeSystemQueues);
    }

    /**
     * Get all queues for a specific connection.
     * @param connectionId The connection ID
     * @param includeSystemQueues Whether to include system queues
     */
    public List<QueueInfo> getAllQueues(String connectionId, boolean includeSystemQueues) throws MQException, IOException {
        MQQueueManager qm = connectionManager.getQueueManager(connectionId);
        return getAllQueuesForManager(qm, includeSystemQueues);
    }

    /**
     * Internal method to get all queues for a given queue manager.
     */
    private List<QueueInfo> getAllQueuesForManager(MQQueueManager qm, boolean includeSystemQueues) throws MQException, IOException {
        List<QueueInfo> queues = new ArrayList<>();

        // Create PCF Message Agent
        PCFMessageAgent agent = new PCFMessageAgent(qm);

        // Create PCF request to inquire queues
        PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);

        // Request all queues (use wildcard)
        request.addParameter(CMQC.MQCA_Q_NAME, "*");
        request.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL);

        // Specify which attributes to retrieve
        request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[]{
                CMQC.MQCA_Q_NAME,
                CMQC.MQIA_Q_TYPE,
                CMQC.MQIA_CURRENT_Q_DEPTH,
                CMQC.MQIA_MAX_Q_DEPTH
        });
        // Send request and get responses
        PCFMessage[] responses = agent.send(request);
        System.out.println("\nFound " + responses.length + " queues:\n");
        for (PCFMessage response : responses) {
            try {
                String queueName = response.getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
                int queueType = response.getIntParameterValue(CMQC.MQIA_Q_TYPE);
                int currentDepth = response.getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                int maxDepth = response.getIntParameterValue(CMQC.MQIA_MAX_Q_DEPTH);

                String queueTypeStr = getQueueTypeString(queueType);

                QueueInfo queueInfo = new QueueInfo(queueName);
                queueInfo.setQueueType(queueType);
                queueInfo.setCurrentDepth(currentDepth);
                queueInfo.setMaxDepth(maxDepth);
                queues.add(queueInfo);
            } catch (Exception e) {
                logger.error("Error", e);
            }
        }
        logger.info("queues: {}", queues.stream().map(QueueInfo::getName).collect(Collectors.joining(",")));
        return queues;
    }

    public QueueInfo getQueueInfo(String queueName) throws MQException, IOException {
        MQQueueManager qm = connectionManager.getQueueManager();

        PCFMessageAgent agent = new PCFMessageAgent(qm);
        try {
            PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
            request.addParameter(MQConstants.MQCA_Q_NAME, queueName);

            PCFMessage[] responses = agent.send(request);

            if (responses.length > 0) {
                QueueInfo queueInfo = new QueueInfo(queueName);
                populateQueueInfo(queueInfo, responses[0]);
                return queueInfo;
            }

            logger.warn("Queue not found: {}", queueName);
            return null;
        } finally {
            agent.disconnect();
        }
    }

    private void populateQueueInfo(QueueInfo queueInfo, PCFMessage response) {
        try {
            queueInfo.setCurrentDepth(response.getIntParameterValue(MQConstants.MQIA_CURRENT_Q_DEPTH));
            queueInfo.setMaxDepth(response.getIntParameterValue(MQConstants.MQIA_MAX_Q_DEPTH));
            queueInfo.setQueueType(response.getIntParameterValue(MQConstants.MQIA_Q_TYPE));
            queueInfo.setOpenInputCount(response.getIntParameterValue(MQConstants.MQIA_OPEN_INPUT_COUNT));
            queueInfo.setOpenOutputCount(response.getIntParameterValue(MQConstants.MQIA_OPEN_OUTPUT_COUNT));

            String desc = response.getStringParameterValue(MQConstants.MQCA_Q_DESC);
            queueInfo.setDescription(desc != null ? desc.trim() : "");

            queueInfo.setAttribute("CreationDate", response.getStringParameterValue(MQConstants.MQCA_CREATION_DATE));
            queueInfo.setAttribute("CreationTime", response.getStringParameterValue(MQConstants.MQCA_CREATION_TIME));
            queueInfo.setAttribute("InhibitPut", response.getIntParameterValue(MQConstants.MQIA_INHIBIT_PUT));
            queueInfo.setAttribute("InhibitGet", response.getIntParameterValue(MQConstants.MQIA_INHIBIT_GET));
            queueInfo.setAttribute("Shareability", response.getIntParameterValue(MQConstants.MQIA_SHAREABILITY));
            queueInfo.setAttribute("DefPriority", response.getIntParameterValue(MQConstants.MQIA_DEF_PRIORITY));
            queueInfo.setAttribute("DefPersistence", response.getIntParameterValue(MQConstants.MQIA_DEF_PERSISTENCE));
            queueInfo.setAttribute("TriggerControl", response.getIntParameterValue(MQConstants.MQIA_TRIGGER_CONTROL));
            queueInfo.setAttribute("MaxMsgLength", response.getIntParameterValue(MQConstants.MQIA_MAX_MSG_LENGTH));

        } catch (Exception e) {
            logger.warn("Error populating queue info for {}", queueInfo.getName(), e);
        }
    }

    public int getQueueDepth(String queueName) throws MQException {
        MQQueueManager qm = connectionManager.getQueueManager();

        int openOptions = MQConstants.MQOO_INQUIRE | MQConstants.MQOO_FAIL_IF_QUIESCING;
        MQQueue queue = null;

        try {
            queue = qm.accessQueue(queueName, openOptions);
            return queue.getCurrentDepth();
        } finally {
            if (queue != null) {
                try {
                    queue.close();
                } catch (MQException e) {
                    logger.warn("Error closing queue: {}", e.getMessage());
                }
            }
        }
    }

    public void refreshQueueInfo(QueueInfo queueInfo) throws MQException, IOException {
        QueueInfo updated = getQueueInfo(queueInfo.getName());
        if (updated != null) {
            queueInfo.setCurrentDepth(updated.getCurrentDepth());
            queueInfo.setMaxDepth(updated.getMaxDepth());
            queueInfo.setOpenInputCount(updated.getOpenInputCount());
            queueInfo.setOpenOutputCount(updated.getOpenOutputCount());
            queueInfo.setAttributes(updated.getAttributes());
        }
    }

    public List<QueueInfo> refreshAllQueues(List<QueueInfo> queues) throws MQException, IOException {
        List<QueueInfo> refreshed = getAllQueues();

        for (QueueInfo queueInfo : queues) {
            for (QueueInfo updated : refreshed) {
                if (queueInfo.getName().equals(updated.getName())) {
                    queueInfo.setCurrentDepth(updated.getCurrentDepth());
                    queueInfo.setMaxDepth(updated.getMaxDepth());
                    queueInfo.setOpenInputCount(updated.getOpenInputCount());
                    queueInfo.setOpenOutputCount(updated.getOpenOutputCount());
                    break;
                }
            }
        }

        return queues;
    }

    private static String getQueueTypeString(int queueType) {
        switch (queueType) {
            case CMQC.MQQT_LOCAL:
                return "LOCAL";
            case CMQC.MQQT_REMOTE:
                return "REMOTE";
            case CMQC.MQQT_ALIAS:
                return "ALIAS";
            case CMQC.MQQT_MODEL:
                return "MODEL";
            case CMQC.MQQT_CLUSTER:
                return "CLUSTER";
            default:
                return "UNKNOWN";
        }
    }

}
