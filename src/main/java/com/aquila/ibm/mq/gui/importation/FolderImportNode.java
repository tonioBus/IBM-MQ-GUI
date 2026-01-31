/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Represents a folder node in the import hierarchy.
 * Contains child nodes which can be other folders or queue browser nodes.
 */
package com.aquila.ibm.mq.gui.importation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@ToString
@Setter
@Getter
@NoArgsConstructor
public class FolderImportNode extends ImportNode {
    private Map<String,ImportNode> children;

}
