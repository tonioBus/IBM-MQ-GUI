package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.model.MessageInfo;
import com.ibm.mq.*;
import com.ibm.mq.constants.MQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final MQConnectionManager connectionManager;
    private static final int DEFAULT_MAX_MESSAGES = 1000;

    public MessageService(MQConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<MessageInfo> browseMessages(String queueName) throws MQException {
        return browseMessages(queueName, DEFAULT_MAX_MESSAGES);
    }

    public List<MessageInfo> browseMessages(String queueName, int maxMessages) throws MQException {
        List<MessageInfo> messages = new ArrayList<>();
        MQQueueManager qm = connectionManager.getQueueManager();

        int openOptions = MQConstants.MQOO_BROWSE | MQConstants.MQOO_FAIL_IF_QUIESCING;
        MQQueue queue = null;

        try {
            queue = qm.accessQueue(queueName, openOptions);
            MQMessage message = new MQMessage();
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_BROWSE_FIRST | MQConstants.MQGMO_NO_WAIT;

            int count = 0;
            while (count < maxMessages) {
                try {
                    message = new MQMessage();
                    queue.get(message, gmo);

                    MessageInfo msgInfo = createMessageInfo(message);
                    messages.add(msgInfo);

                    gmo.options = MQConstants.MQGMO_BROWSE_NEXT | MQConstants.MQGMO_NO_WAIT;
                    count++;
                } catch (MQException mqe) {
                    if (mqe.getReason() == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                        break;
                    }
                    throw mqe;
                }
            }

            logger.info("Browsed {} messages from queue {}", messages.size(), queueName);
        } finally {
            if (queue != null) {
                try {
                    queue.close();
                } catch (MQException e) {
                    logger.warn("Error closing queue: {}", e.getMessage());
                }
            }
        }

        return messages;
    }

    public void putMessage(String queueName, String messageData, int priority, int persistence) throws MQException, IOException {
        MQQueueManager qm = connectionManager.getQueueManager();

        int openOptions = MQConstants.MQOO_OUTPUT | MQConstants.MQOO_FAIL_IF_QUIESCING;
        MQQueue queue = null;

        try {
            queue = qm.accessQueue(queueName, openOptions);

            MQMessage message = new MQMessage();
            message.format = MQConstants.MQFMT_STRING;
            message.priority = priority;
            message.persistence = persistence;
            message.writeString(messageData);

            MQPutMessageOptions pmo = new MQPutMessageOptions();
            queue.put(message, pmo);

            logger.info("Put message to queue {}, size: {} bytes", queueName, messageData.length());
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

    public void putMessage(String queueName, byte[] messageData, int priority, int persistence) throws MQException, IOException {
        MQQueueManager qm = connectionManager.getQueueManager();

        int openOptions = MQConstants.MQOO_OUTPUT | MQConstants.MQOO_FAIL_IF_QUIESCING;
        MQQueue queue = null;

        try {
            queue = qm.accessQueue(queueName, openOptions);

            MQMessage message = new MQMessage();
            message.format = MQConstants.MQFMT_NONE;
            message.priority = priority;
            message.persistence = persistence;
            message.write(messageData);

            MQPutMessageOptions pmo = new MQPutMessageOptions();
            queue.put(message, pmo);

            logger.info("Put binary message to queue {}, size: {} bytes", queueName, messageData.length);
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

    public MessageInfo getMessage(String queueName, byte[] messageId) throws MQException {
        MQQueueManager qm = connectionManager.getQueueManager();

        int openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_FAIL_IF_QUIESCING;
        MQQueue queue = null;

        try {
            queue = qm.accessQueue(queueName, openOptions);

            MQMessage message = new MQMessage();
            message.messageId = messageId;

            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_NO_WAIT;
            gmo.matchOptions = MQConstants.MQMO_MATCH_MSG_ID;

            queue.get(message, gmo);

            return createMessageInfo(message);
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

    private MessageInfo createMessageInfo(MQMessage message) {
        MessageInfo msgInfo = new MessageInfo();
        msgInfo.setMessageId(message.messageId);
        msgInfo.setCorrelationId(message.correlationId);
//        msgInfo.setFormat(message.format);
        msgInfo.setPriority(message.priority);
        msgInfo.setPersistence(message.persistence);
        msgInfo.setEncoding(message.encoding);
        msgInfo.setCharacterSet(message.characterSet);
        msgInfo.setTimestamp(LocalDateTime.now());

        try {
            int messageLength = message.getMessageLength();
            msgInfo.setMessageLength(messageLength);

            byte[] messageBytes = new byte[messageLength];
            message.readFully(messageBytes);
            msgInfo.setMessageBytes(messageBytes);

            if (message.format.trim().equals(MQConstants.MQFMT_STRING) ||
//                message.characterSet == MQConstants.CCSID_UTF8 ||
                message.characterSet == 1208) {
                String messageData = new String(messageBytes, StandardCharsets.UTF_8);
                msgInfo.setMessageData(messageData);
            } else {
                msgInfo.setMessageData(new String(messageBytes, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            logger.error("Error reading message data", e);
            msgInfo.setMessageData("Error reading message: " + e.getMessage());
        }

        return msgInfo;
    }

    public int getMessageCount(String queueName) throws MQException {
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
}
