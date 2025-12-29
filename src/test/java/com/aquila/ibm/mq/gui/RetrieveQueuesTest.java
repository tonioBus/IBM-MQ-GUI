package com.aquila.ibm.mq.gui;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Hashtable;

@Slf4j
public class RetrieveQueuesTest {

    @Test
    void testRetrieveAllQueues() {
        String queueManagerName = "QM1";
        String host = "localhost";
        int port = 1414;
        String channel = "DEV.APP.SVRCONN";

        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(CMQC.CHANNEL_PROPERTY, channel);
        properties.put(CMQC.HOST_NAME_PROPERTY, host);
        properties.put(CMQC.PORT_PROPERTY, port);
        properties.put(CMQC.TRANSPORT_PROPERTY, CMQC.TRANSPORT_MQSERIES_CLIENT);
        properties.put(CMQC.USER_ID_PROPERTY, "app");
        properties.put(CMQC.PASSWORD_PROPERTY, "passw0rd");

        MQQueueManager queueManager = null;
        PCFMessageAgent agent = null;

        try {
            // Connect to Queue Manager
            queueManager = new MQQueueManager(queueManagerName, properties);
            log.info("Connected to Queue Manager: " + queueManagerName);

            // Create PCF Message Agent
            agent = new PCFMessageAgent(queueManager);

            // Create PCF request to inquire queues
            PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);

            // Request all queues (use wildcard)
            request.addParameter(CMQC.MQCA_Q_NAME, "*");

            // Specify which attributes to retrieve
            request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[] {
                CMQC.MQCA_Q_NAME,
                CMQC.MQIA_Q_TYPE,
                CMQC.MQIA_CURRENT_Q_DEPTH,
                CMQC.MQIA_MAX_Q_DEPTH
            });

            // Send request and get responses
            PCFMessage[] responses = agent.send(request);

            System.out.println("\nFound " + responses.length + " queues:\n");
            System.out.println(String.format("%-50s %-15s %-10s %-10s",
                "Queue Name", "Type", "Depth", "Max Depth"));
            System.out.println("-".repeat(90));

            for (PCFMessage response : responses) {
                String queueName = response.getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
                int queueType = response.getIntParameterValue(CMQC.MQIA_Q_TYPE);
                int currentDepth = response.getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                int maxDepth = response.getIntParameterValue(CMQC.MQIA_MAX_Q_DEPTH);

                String queueTypeStr = getQueueTypeString(queueType);

                System.out.println(String.format("%-50s %-15s %-10d %-10d",
                    queueName, queueTypeStr, currentDepth, maxDepth));
            }

        } catch (MQException e) {
            System.err.println("MQ Exception occurred:");
            System.err.println("Completion Code: " + e.getCompCode());
            System.err.println("Reason Code: " + e.getReason());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IO Exception occurred:");
            e.printStackTrace();
        } finally {
            try {
                if (agent != null) {
                    agent.disconnect();
                }
                if (queueManager != null && queueManager.isConnected()) {
                    queueManager.disconnect();
                    System.out.println("\nDisconnected from Queue Manager");
                }
            } catch (MQException e) {
                e.printStackTrace();
            }
        }
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