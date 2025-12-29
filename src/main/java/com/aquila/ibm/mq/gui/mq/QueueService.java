package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.ibm.mq.*;
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

@Slf4j
public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private final MQConnectionManager connectionManager;

    public QueueService(MQConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<QueueInfo> getAllQueues() throws MQException, IOException {
        return getAllQueues(false);
    }

    public List<QueueInfo> getAllQueues(boolean includeSystemQueues) throws MQException, IOException {
        List<QueueInfo> queues = new ArrayList<>();
        MQQueueManager qm = connectionManager.getQueueManager();

        // Open command queue for putting request
        MQQueue commandQueue = qm.accessQueue("SYSTEM.ADMIN.COMMAND.QUEUE",
                MQConstants.MQOO_OUTPUT | MQConstants.MQOO_FAIL_IF_QUIESCING);

        // Open reply queue (temporary dynamic queue)
        MQQueue replyQueue = qm.accessQueue("SYSTEM.DEFAULT.MODEL.QUEUE",
                MQConstants.MQOO_INPUT_EXCLUSIVE | MQConstants.MQOO_FAIL_IF_QUIESCING);

        try {
            // Build PCF request message
            PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
            request.addParameter(MQConstants.MQCA_Q_NAME, "*");
            request.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL);

            // Set reply queue in message descriptor
            MQMessage mqRequest = new MQMessage();
            mqRequest.format = MQConstants.MQFMT_ADMIN;
            mqRequest.replyToQueueName = replyQueue.name;
            request.write(mqRequest);

            // Send request
            MQPutMessageOptions pmo = new MQPutMessageOptions();
            commandQueue.put(mqRequest, pmo);

            // Read replies
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.waitInterval = 5000; // 5 seconds
            gmo.options = MQConstants.MQGMO_WAIT | MQConstants.MQGMO_CONVERT;

            while (true) {
                try {
                    MQMessage replyMsg = new MQMessage();
                    replyQueue.get(replyMsg, gmo);

                    PCFMessage response = new PCFMessage(replyMsg);
                    log.info("Received response from command queue: {}", response.toString());
                    // Process response...

                } catch (MQException e) {
                    logger.error("Error while processing command queue.", e);
                    log.error("Error while processing command queue.", e);
                    if (e.getReason() == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                        break; // No more messages
                    }
                    throw e;
                }
            }
        } finally {
            commandQueue.close();
            replyQueue.close();
        }
        logger.info("queues: {}", queues.stream().map(queueInfo -> queueInfo.getName()).collect(Collectors.joining(",")));
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
            logger.warn("Error populating queue info for {}: {}", queueInfo.getName(), e.getMessage());
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

        for (QueueInfo existing : queues) {
            for (QueueInfo updated : refreshed) {
                if (existing.getName().equals(updated.getName())) {
                    existing.setCurrentDepth(updated.getCurrentDepth());
                    existing.setMaxDepth(updated.getMaxDepth());
                    existing.setOpenInputCount(updated.getOpenInputCount());
                    existing.setOpenOutputCount(updated.getOpenOutputCount());
                    break;
                }
            }
        }

        return queues;
    }
}
