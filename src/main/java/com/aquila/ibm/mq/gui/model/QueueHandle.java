package com.aquila.ibm.mq.gui.model;

import lombok.*;

/**
 * Represents a handle (connection) to a queue, showing which process/application is using it.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class QueueHandle {
    private String queueName;
    private String applicationName;
    private int processId;
    private int threadId;
    private String userId;
    private String channelName;
    private int openOptions;
    private String connectionId;

    // Open options flags
    private boolean openForInput;
    private boolean openForOutput;
    private boolean openForBrowse;
    private boolean openForInquire;

    /**
     * Returns a human-readable description of the open options.
     */
    public String getOpenOptionsDescription() {
        StringBuilder sb = new StringBuilder();
        if (openForInput) sb.append("INPUT ");
        if (openForOutput) sb.append("OUTPUT ");
        if (openForBrowse) sb.append("BROWSE ");
        if (openForInquire) sb.append("INQUIRE ");
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return String.format("%s (PID: %d, User: %s, Options: %s)",
            applicationName, processId, userId, getOpenOptionsDescription());
    }
}
