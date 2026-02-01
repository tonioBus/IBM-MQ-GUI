/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Service for IBM MQ queue operations using PCF (Programmable Command Format).
 * Retrieves queue lists, queue properties, queue handles, and supports
 * optimized batch queries with pattern-based filtering.
 */
package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.QueueHandle;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.util.QueueNameRegexCalculator;
import com.google.common.collect.Lists;
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

import java.io.IOException;
import java.util.*;

@Slf4j
public class QueueService {
    private final MQConnectionManager connectionManager;

    public QueueService(MQConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Get all queues for the active connection.
     */
    public List<QueueInfo> getAllQueues(String ibmPattern) throws MQException, IOException, MQDataException {
        return getAllQueues(ibmPattern, false);
    }

    /**
     * Get all queues for the active connection.
     */
    public List<QueueInfo> getAllQueues(String ibmPattern, boolean includeSystemQueues) throws MQException, IOException, MQDataException {
        final MQQueueManager qm = connectionManager.getQueueManager();
        return getAllQueuesForManager(qm, ibmPattern, includeSystemQueues);
    }

    /**
     * Get all queues for a specific connection.
     *
     * @param connectionId        The connection ID
     * @param includeSystemQueues Whether to include system queues
     */
    public List<QueueInfo> getAllQueues(String connectionId, String ibmPattern, boolean includeSystemQueues) throws MQException, IOException, MQDataException {
        final MQQueueManager qm = connectionManager.getQueueManager(connectionId);
        return getAllQueuesForManager(qm, ibmPattern, includeSystemQueues);
    }

    /**
     * Internal method to get all queues for a given queue manager.
     */
    private List<QueueInfo> getAllQueuesForManager(MQQueueManager qm, String ibmPattern, boolean includeSystemQueues) throws MQException, IOException, MQDataException {
        final List<QueueInfo> queues = new ArrayList<>();

        final PCFMessageAgent agent = connectionManager.getActiveAgent();

        // Create PCF request to inquire queues
        final PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);

        // Request all queues (use wildcard)
        log.info("Using IBM Pattern: {}", ibmPattern);
        request.addParameter(CMQC.MQCA_Q_NAME, ibmPattern);
        request.addParameter(MQConstants.MQIA_Q_TYPE, CMQC.MQQT_ALL); //  | MQConstants.MQQT_ALIAS | MQConstants.MQQT_REMOTE);

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
        log.info("After agent.send() -> Found  {} queues.", responses.length);
        for (final PCFMessage response : responses) {
            try {
                final String queueName = response.getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
                final QueueInfo queueInfo = new QueueInfo(queueName);
                int queueType = response.getIntParameterValue(CMQC.MQIA_Q_TYPE);
                if (queueType == CMQC.MQQT_LOCAL) {
                    int currentDepth = response.getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                    int maxDepth = response.getIntParameterValue(CMQC.MQIA_MAX_Q_DEPTH);
                    queueInfo.setCurrentDepth(currentDepth);
                    queueInfo.setMaxDepth(maxDepth);
                }
                queueInfo.setQueueType(queueType);
                queues.add(queueInfo);
            } catch (Exception e) {
                log.error("Error", e);
            }
        }
        return queues;
    }

    public QueueInfo getQueueInfo(String queueName) throws IOException, MQDataException {
        final MQQueueManager qm = connectionManager.getQueueManager();

        final PCFMessageAgent agent = connectionManager.getActiveAgent();
        final QueueInfo queueInfo = new QueueInfo(queueName);
        try {
            final PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
            request.addParameter(MQConstants.MQCA_Q_NAME, queueName);

            final PCFMessage[] responses = agent.send(request);
            if (responses.length > 0) {
                populateQueueInfo(queueInfo, responses[0]);
                return queueInfo;
            }
            log.warn("Queue not found: {}", queueName);
            queueInfo.setAttribute("Exception", "Queue not found");
            return queueInfo;
        } catch (PCFException e) {
            log.error("Queue not found: {}", queueName, e);
            queueInfo.setAttribute("Exception", e.getMessage());
            return queueInfo;
        }
    }

    /**
     * Get queue information for a list of queue names.
     *
     * @param queueInfos List of queue names to retrieve information for
     * @return List of QueueInfo objects for the specified queues
     */
    public void populateQueueInfosShort(List<QueueInfo> queueInfos, int nbThread, boolean sequentialRequest) {
        if (queueInfos == null || queueInfos.isEmpty()) {
            return;
        }
        final MQQueueManager qm = connectionManager.getQueueManager();
        long startTime = System.currentTimeMillis();
        if (sequentialRequest) {
            if (nbThread > 1 && (nbThread < queueInfos.size() * 2)) {
                multiThreadRequest(qm, queueInfos, nbThread);
            } else {
                final PCFMessageAgent agent = connectionManager.getActiveAgent();
                populateQueueInfosShort(agent, queueInfos);
            }
        } else {
            List<String> queueNames = queueInfos.stream().map(QueueInfo::getQueue).toList();
            List<String> optimizedIBMMQPatterns = QueueNameRegexCalculator.createOptimizedIBMMQPatterns(queueNames);
            final PCFMessageAgent agent = connectionManager.getActiveAgent();
            for (final String optimizedIBMMQPattern : optimizedIBMMQPatterns) {
                final PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
                request.addParameter(MQConstants.MQCA_Q_NAME, optimizedIBMMQPattern);
                log.info("Before agent.send() optimise ({})", optimizedIBMMQPattern);
                try {
                    final PCFMessage[] responses = agent.send(request);
                    log.info("After agent.send() optimise responses: {}", responses.length);
                    populateQueueInfosShort(queueInfos, responses);
                } catch (Exception e) {
                    log.error("Error retrieving queues for pattern: {} ", optimizedIBMMQPattern, e);
                }
            }
        }
        long endTime = System.currentTimeMillis();
        log.info("Retrieved information for {} out of {} queues in {} ms", queueInfos.size(), queueInfos.size(), endTime - startTime);
    }

    void multiThreadRequest(MQQueueManager qm, List<QueueInfo> queueInfos, int nbThread) {
        final int nbParts = queueInfos.size() / nbThread + queueInfos.size() % nbThread;
        final List<List<QueueInfo>> listPerThread = Lists.partition(queueInfos, nbParts);
        log.info("Creating {} sub-list", listPerThread.size());
        final PCFMessageAgent agent = connectionManager.getActiveAgent();
        listPerThread.parallelStream().forEach(tmpQueueInfos -> {
            //log.info("multiThreadRequest -> create agent {}", tmpQueueInfos.get(0).getQueue());
            populateQueueInfosShort(agent, tmpQueueInfos);
            //log.info("End multiThreadRequest: {}", tmpQueueInfos.get(0).getQueue());
        });
    }

    void populateQueueInfosShort(final PCFMessageAgent agent, List<QueueInfo> queueInfos) {
        for (final QueueInfo queueInfo : queueInfos) {
            final PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
            request.addParameter(MQConstants.MQCA_Q_NAME, queueInfo.getQueue());
            request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[]{
                    CMQC.MQCA_Q_NAME,
                    CMQC.MQIA_Q_TYPE,
                    CMQC.MQIA_CURRENT_Q_DEPTH,
                    CMQC.MQIA_MAX_Q_DEPTH
            });
            try {
                final PCFMessage[] responses = agent.send(request);
                populateQueueInfoShort(queueInfo, responses[0]);
            } catch (Exception e) {
                log.warn("Error retrieving queues: {} ", queueInfo.getQueue(), e);
            }
        }
    }

    private void populateQueueInfosShort(List<QueueInfo> queueInfos, PCFMessage[] responses) {
        final Map<String, QueueInfo> mapQueuesInfos = new HashMap<>();
        queueInfos.forEach(queueInfo -> mapQueuesInfos.put(queueInfo.getQueue(), queueInfo));
        Arrays.stream(responses).parallel().forEach(response -> {
            String queueName = null;
            try {
                queueName = response.getStringParameterValue(MQConstants.MQCA_Q_NAME).trim();
                final QueueInfo queueInfo = mapQueuesInfos.get(queueName);
                if (queueInfo != null) {
                    log.info("populateQueueInfo({}) -> {}", queueName, queueInfo);
                    populateQueueInfoShort(queueInfo, response);
                } else
                    log.warn("QueueName:{} found on PCF agent response but not part of the list: ignored", queueName);
            } catch (PCFException e) {
                log.error("Can not retrieve queue name", e);
            }
        });
    }

    private void populateQueueInfoShort(QueueInfo queueInfo, PCFMessage response) throws PCFException {
        queueInfo.setQueueType(response.getIntParameterValue(MQConstants.MQIA_Q_TYPE));
        if (queueInfo.getQueueType() == CMQC.MQQT_LOCAL) {
            queueInfo.setCurrentDepth(retrieveIntParameter(queueInfo, response, MQConstants.MQIA_CURRENT_Q_DEPTH, "MQIA_CURRENT_Q_DEPTH"));
            queueInfo.setMaxDepth(retrieveIntParameter(queueInfo, response, MQConstants.MQIA_MAX_Q_DEPTH, "MQIA_MAX_Q_DEPTH"));
        }
    }

    private void populateQueueInfo(QueueInfo queueInfo, PCFMessage response) {
        try {
            queueInfo.setQueueType(response.getIntParameterValue(MQConstants.MQIA_Q_TYPE));
            if (queueInfo.getQueueType() == CMQC.MQQT_LOCAL) {
                queueInfo.setCurrentDepth(retrieveIntParameter(queueInfo, response, MQConstants.MQIA_CURRENT_Q_DEPTH, "MQIA_CURRENT_Q_DEPTH"));
                queueInfo.setMaxDepth(retrieveIntParameter(queueInfo, response, MQConstants.MQIA_MAX_Q_DEPTH, "MQIA_MAX_Q_DEPTH"));
                queueInfo.setOpenInputCount(retrieveIntParameter(queueInfo, response, MQConstants.MQIA_OPEN_INPUT_COUNT, "MQIA_OPEN_INPUT_COUNT"));
                queueInfo.setOpenOutputCount(retrieveIntParameter(queueInfo, response, MQConstants.MQIA_OPEN_OUTPUT_COUNT, "MQIA_OPEN_OUTPUT_COUNT"));
            }
            final String desc = response.getStringParameterValue(MQConstants.MQCA_Q_DESC);
            queueInfo.setDescription(desc != null ? desc.trim() : "");

            // Retrieve base queue name for alias queues
            if (queueInfo.getQueueType() == MQConstants.MQQT_ALIAS) {
                try {
                    final String baseQueueName = response.getStringParameterValue(MQConstants.MQCA_BASE_Q_NAME);
                    queueInfo.setBaseQueueName(baseQueueName != null ? baseQueueName.trim() : null);
                } catch (PCFException e) {
                    queueInfo.setAttribute("Exception", e.getMessage());
                    log.debug("Base queue name not available for {}", queueInfo.getQueue());
                }
            }
            if (queueInfo.getQueueType() == MQConstants.MQQT_LOCAL) {
                queueInfo.setAttribute("CreationDate", response.getStringParameterValue(MQConstants.MQCA_CREATION_DATE));
                queueInfo.setAttribute("CreationTime", response.getStringParameterValue(MQConstants.MQCA_CREATION_TIME));
                setQueueInfoSetIntAttribute(queueInfo, response, "Shareability", MQConstants.MQIA_SHAREABILITY);
                setQueueInfoSetIntAttribute(queueInfo, response, "TriggerControl", MQConstants.MQIA_TRIGGER_CONTROL);
                setQueueInfoSetIntAttribute(queueInfo, response, "MaxMsgLength", MQConstants.MQIA_MAX_MSG_LENGTH);
            }
            setQueueInfoSetIntAttribute(queueInfo, response, "InhibitPut", MQConstants.MQIA_INHIBIT_PUT);
            setQueueInfoSetIntAttribute(queueInfo, response, "InhibitGet", MQConstants.MQIA_INHIBIT_GET);
            setQueueInfoSetIntAttribute(queueInfo, response, "DefPriority", MQConstants.MQIA_DEF_PRIORITY);
            setQueueInfoSetIntAttribute(queueInfo, response, "DefPersistence", MQConstants.MQIA_DEF_PERSISTENCE);

        } catch (Exception e) {
            queueInfo.setAttribute("Exception", e.getMessage());
            log.warn("Error populating queue info for {}", queueInfo.getQueue(), e);
        }
    }

    private int retrieveIntParameter(QueueInfo queueInfo, PCFMessage response, int ibmParameter, String mqiaCurrentQDepth) {
        try {
            return response.getIntParameterValue(ibmParameter);
        } catch (PCFException e) {
            queueInfo.setAttribute("Exception", e.getMessage());
            log.warn("Attribute {} not available for {}", mqiaCurrentQDepth, queueInfo.getQueue());
            return -1;
        }
    }

    private void setQueueInfoSetIntAttribute(QueueInfo queueInfo, PCFMessage response, String queueInfoParameter, int ibmParameter) {
        try {
            queueInfo.setAttribute(queueInfoParameter, response.getIntParameterValue(ibmParameter));
        } catch (PCFException e) {
            queueInfo.setAttribute("Exception", e.getMessage());
            log.warn("Attribute {} not available for {}", queueInfoParameter, queueInfo.getQueue());
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
        final List<QueueInfo> refreshed = getAllQueues("*");

        for (final QueueInfo queueInfo : queues) {
            for (final QueueInfo updated : refreshed) {
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

    /**
     * Get all handles (processes/applications) currently using a queue.
     *
     * @param queueName The name of the queue to query
     * @return List of QueueHandle objects representing each connection to the queue
     */
    public List<QueueHandle> getQueueHandles(String queueName) throws IOException, MQDataException {
        final List<QueueHandle> handles = new ArrayList<>();
        final MQQueueManager qm = connectionManager.getQueueManager();
        final PCFMessageAgent agent = new PCFMessageAgent(qm);

        try {
            final PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_STATUS);
            request.addParameter(MQConstants.MQCA_Q_NAME, queueName);
            // MQQSOT_HANDLE = 1 (handle status type)
            request.addParameter(CMQCFC.MQIACF_Q_STATUS_TYPE, 1);

            final PCFMessage[] responses = agent.send(request);
            log.info("Found {} handles for queue {}", responses.length, queueName);

            for (final PCFMessage response : responses) {
                try {
                    final QueueHandle handle = parseQueueHandle(response, queueName);
                    handles.add(handle);
                } catch (Exception e) {
                    log.warn("Error parsing handle for queue {}", queueName, e);
                }
            }
        } catch (PCFException e) {
            // MQRCCF_CHL_STATUS_NOT_FOUND or similar - no handles open
            if (e.getReason() == MQConstants.MQRCCF_CHL_STATUS_NOT_FOUND ||
                    e.getReason() == MQConstants.MQRC_UNKNOWN_OBJECT_NAME) {
                log.debug("No handles found for queue {}", queueName);
            } else {
                throw e;
            }
        } finally {
            agent.disconnect();
        }

        return handles;
    }

    private QueueHandle parseQueueHandle(PCFMessage response, String queueName) {
        final QueueHandle.QueueHandleBuilder builder = QueueHandle.builder();

        // Queue name (may differ from input if wildcard was used)
        try {
            builder.queueName(response.getStringParameterValue(MQConstants.MQCA_Q_NAME).trim());
        } catch (PCFException e) {
            builder.queueName(queueName);
        }

        // Application name/tag
        try {
            builder.applicationName(response.getStringParameterValue(MQConstants.MQCACF_APPL_TAG).trim());
        } catch (PCFException e) {
            builder.applicationName("Unknown");
        }

        // Process ID
        try {
            builder.processId(response.getIntParameterValue(MQConstants.MQIACF_PROCESS_ID));
        } catch (PCFException e) {
            builder.processId(0);
        }

        // Thread ID
        try {
            builder.threadId(response.getIntParameterValue(MQConstants.MQIACF_THREAD_ID));
        } catch (PCFException e) {
            builder.threadId(0);
        }

        // User ID
        try {
            builder.userId(response.getStringParameterValue(MQConstants.MQCACF_USER_IDENTIFIER).trim());
        } catch (PCFException e) {
            builder.userId("Unknown");
        }

        // Channel name (for client connections)
        try {
            builder.channelName(response.getStringParameterValue(MQConstants.MQCACH_CHANNEL_NAME).trim());
        } catch (PCFException e) {
            builder.channelName("");
        }

        // Connection ID
        try {
            byte[] connId = response.getBytesParameterValue(MQConstants.MQBACF_CONNECTION_ID);
            builder.connectionId(bytesToHex(connId));
        } catch (PCFException e) {
            builder.connectionId("");
        }

        // Open options
        try {
            final int openOptions = response.getIntParameterValue(MQConstants.MQIACF_OPEN_OPTIONS);
            builder.openOptions(openOptions);
            builder.openForInput((openOptions & MQConstants.MQOO_INPUT_AS_Q_DEF) != 0 ||
                    (openOptions & MQConstants.MQOO_INPUT_SHARED) != 0 ||
                    (openOptions & MQConstants.MQOO_INPUT_EXCLUSIVE) != 0);
            builder.openForOutput((openOptions & MQConstants.MQOO_OUTPUT) != 0);
            builder.openForBrowse((openOptions & MQConstants.MQOO_BROWSE) != 0);
            builder.openForInquire((openOptions & MQConstants.MQOO_INQUIRE) != 0);
        } catch (PCFException e) {
            builder.openOptions(0);
        }

        return builder.build();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        final StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

}
