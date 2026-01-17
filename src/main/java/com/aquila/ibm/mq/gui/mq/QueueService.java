package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.util.QueueNameRegexCalculator;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@Slf4j
public class QueueService {
    private static final Logger log = LoggerFactory.getLogger(QueueService.class);
    private final MQConnectionManager connectionManager;

    public QueueService(MQConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Get all queues for the active connection.
     */
    public List<QueueInfo> getAllQueues() throws MQException, IOException, MQDataException {
        return getAllQueues(false);
    }

    /**
     * Get all queues for the active connection.
     */
    public List<QueueInfo> getAllQueues(boolean includeSystemQueues) throws MQException, IOException, MQDataException {
        MQQueueManager qm = connectionManager.getQueueManager();
        return getAllQueuesForManager(qm, includeSystemQueues);
    }

    /**
     * Get all queues for a specific connection.
     *
     * @param connectionId        The connection ID
     * @param includeSystemQueues Whether to include system queues
     */
    public List<QueueInfo> getAllQueues(String connectionId, boolean includeSystemQueues) throws MQException, IOException, MQDataException {
        MQQueueManager qm = connectionManager.getQueueManager(connectionId);
        return getAllQueuesForManager(qm, includeSystemQueues);
    }

    /**
     * Internal method to get all queues for a given queue manager.
     */
    private List<QueueInfo> getAllQueuesForManager(MQQueueManager qm, boolean includeSystemQueues) throws MQException, IOException, MQDataException {
        final List<QueueInfo> queues = new ArrayList<>();

        // Create PCF Message Agent
        final PCFMessageAgent agent = new PCFMessageAgent(qm);

        // Create PCF request to inquire queues
        final PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);

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
        log.info("Before agent.send()");
        final PCFMessage[] responses = agent.send(request);
        log.info("\nFound  {} queues.", responses.length);
        for (PCFMessage response : responses) {
            try {
                final String queueName = response.getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
                int queueType = response.getIntParameterValue(CMQC.MQIA_Q_TYPE);
                int currentDepth = response.getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                int maxDepth = response.getIntParameterValue(CMQC.MQIA_MAX_Q_DEPTH);

                final QueueInfo queueInfo = new QueueInfo(queueName);
                queueInfo.setQueueType(queueType);
                queueInfo.setCurrentDepth(currentDepth);
                queueInfo.setMaxDepth(maxDepth);
                queueInfo.setQueueType(queueType);
                queues.add(queueInfo);
            } catch (Exception e) {
                log.error("Error", e);
            }
        }
        return queues;
    }

    public QueueInfo getQueueInfo(String queueName) throws MQException, IOException, MQDataException {
        final MQQueueManager qm = connectionManager.getQueueManager();

        final PCFMessageAgent agent = new PCFMessageAgent(qm);
        try {
            final PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
            request.addParameter(MQConstants.MQCA_Q_NAME, queueName);

            final PCFMessage[] responses = agent.send(request);

            if (responses.length > 0) {
                final QueueInfo queueInfo = new QueueInfo(queueName);
                populateQueueInfo(queueInfo, responses[0]);
                return queueInfo;
            }

            log.warn("Queue not found: {}", queueName);
            return null;
        } finally {
            agent.disconnect();
        }
    }

    /**
     * Get queue information for a list of queue names.
     *
     * @param queueInfos List of queue names to retrieve information for
     * @return List of QueueInfo objects for the specified queues
     */
    public void populateQueueInfos(List<QueueInfo> queueInfos, boolean sequentialRequest) throws MQDataException, IOException {
        if (queueInfos == null || queueInfos.isEmpty()) {
            return;
        }
        final MQQueueManager qm = connectionManager.getQueueManager();
        final PCFMessageAgent agent = new PCFMessageAgent(qm);
        long startTime = System.currentTimeMillis();
        if (sequentialRequest) {
            for (final QueueInfo queueInfo : queueInfos) {
                final PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
                request.addParameter(MQConstants.MQCA_Q_NAME, queueInfo.getQueue());
                log.info("Before agent.send()");
                final PCFMessage[] responses = agent.send(request);
                log.info("After agent.send() responses;{}", responses.length);
                populateQueueInfo(queueInfo, responses[0]);
            }
        } else {
            List<String> queueNames = queueInfos.stream().map(QueueInfo::getQueue).toList();
            List<String> optimizedIBMMQPatterns = QueueNameRegexCalculator.createOptimizedIBMMQPatterns(queueNames);
            for (final String optimizedIBMMQPattern : optimizedIBMMQPatterns) {
                final PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
                request.addParameter(MQConstants.MQCA_Q_NAME, optimizedIBMMQPattern);
                log.info("Before agent.send() optimise ({})", optimizedIBMMQPattern);
                final PCFMessage[] responses = agent.send(request);
                log.info("After agent.send() optimise responses;{}", responses.length);
                populateQueueInfos(queueInfos, responses);
            }
        }
        long endTime = System.currentTimeMillis();
        log.info("Retrieved information for {} out of {} queues in {} ms", queueInfos.size(), queueInfos.size(), endTime - startTime);
    }

    private void populateQueueInfos(List<QueueInfo> queueInfos, PCFMessage[] responses) {
        Map<String, QueueInfo> mapQueuesInfos = new HashMap<>();
        queueInfos.forEach(queueInfo -> mapQueuesInfos.put(queueInfo.getQueue(), queueInfo));
        Arrays.stream(responses).parallel().forEach(response -> {
            String queueName = null;
            try {
                queueName = response.getStringParameterValue(MQConstants.MQCA_Q_NAME).trim();
                QueueInfo queueInfo = mapQueuesInfos.get(queueName);
                if (queueInfo != null) {
                    log.info("populateQueueInfo({}) -> {}", queueName, queueInfo);
                    populateQueueInfo(queueInfo, response);
                } else log.error("QueueName:{} not found on PCF agent response", queueName);
            } catch (PCFException e) {
                log.error("Can not retrieve queue name", e);
            }
        });
    }

    private void populateQueueInfo(QueueInfo queueInfo, PCFMessage response) {
        try {
            queueInfo.setCurrentDepth(response.getIntParameterValue(MQConstants.MQIA_CURRENT_Q_DEPTH));
            queueInfo.setMaxDepth(response.getIntParameterValue(MQConstants.MQIA_MAX_Q_DEPTH));
            queueInfo.setQueueType(response.getIntParameterValue(MQConstants.MQIA_Q_TYPE));
            queueInfo.setOpenInputCount(response.getIntParameterValue(MQConstants.MQIA_OPEN_INPUT_COUNT));
            queueInfo.setOpenOutputCount(response.getIntParameterValue(MQConstants.MQIA_OPEN_OUTPUT_COUNT));

            final String desc = response.getStringParameterValue(MQConstants.MQCA_Q_DESC);
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
            log.warn("Error populating queue info for {}", queueInfo.getQueue(), e);
        }
    }

    public void refreshQueueInfo(QueueInfo queueInfo) throws MQException, IOException, MQDataException {
        final QueueInfo updated = getQueueInfo(queueInfo.getQueue());
        if (updated != null) {
            queueInfo.setCurrentDepth(updated.getCurrentDepth());
            queueInfo.setMaxDepth(updated.getMaxDepth());
            queueInfo.setOpenInputCount(updated.getOpenInputCount());
            queueInfo.setOpenOutputCount(updated.getOpenOutputCount());
            queueInfo.setAttributes(updated.getAttributes());
        }
    }

    public void refreshAllQueues(List<QueueInfo> queues) throws MQException, IOException, MQDataException {
        final List<QueueInfo> refreshed = getAllQueues();

        for (QueueInfo queueInfo : queues) {
            for (QueueInfo updated : refreshed) {
                if (queueInfo.getQueue().equals(updated.getQueue())) {
                    queueInfo.setCurrentDepth(updated.getCurrentDepth());
                    queueInfo.setMaxDepth(updated.getMaxDepth());
                    queueInfo.setOpenInputCount(updated.getOpenInputCount());
                    queueInfo.setOpenOutputCount(updated.getOpenOutputCount());
                    break;
                }
            }
        }

    }

    private static String getQueueTypeString(int queueType) {
        return switch (queueType) {
            case CMQC.MQQT_LOCAL -> "LOCAL";
            case CMQC.MQQT_REMOTE -> "REMOTE";
            case CMQC.MQQT_ALIAS -> "ALIAS";
            case CMQC.MQQT_MODEL -> "MODEL";
            case CMQC.MQQT_CLUSTER -> "CLUSTER";
            default -> "UNKNOWN";
        };
    }

}
