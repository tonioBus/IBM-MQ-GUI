/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Represents a queue browser node in the import hierarchy.
 * References a queue manager and contains a map of queue names
 * to their display labels.
 */
package com.aquila.ibm.mq.gui.importation;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@ToString
@Setter
@Getter
@NoArgsConstructor
public class QueuesImportNode extends ImportNode {
    private String queueMgr;
    private Map<String,String> children;
}
