/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Base class for import hierarchy nodes using Jackson polymorphic deserialization.
 * Subclasses include FolderImportNode and QueuesImportNode for
 * different node types in the import JSON structure.
 */
package com.aquila.ibm.mq.gui.importation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FolderImportNode.class, name = "folder"),
        @JsonSubTypes.Type(value = QueuesImportNode.class, name = "queue")
})
@Getter
public class ImportNode {

    private String type;
}
