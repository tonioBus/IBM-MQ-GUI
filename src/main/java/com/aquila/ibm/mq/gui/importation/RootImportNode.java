/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Root node for configuration import using Jackson.
 * Contains the label, queue manager configurations map,
 * and hierarchy structure for JSON deserialization.
 */
package com.aquila.ibm.mq.gui.importation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@ToString
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RootImportNode {

    @JsonIgnore
    private static ObjectMapper mapper = new ObjectMapper();

    private String label;
    private Map<String, QueueManagerConfigNode> queueManagers;
    private Map<String, ImportNode> hierarchy;

    @JsonIgnore
    public static RootImportNode from(File file) throws IOException {
        return mapper.readValue(file, RootImportNode.class);
    }

}
