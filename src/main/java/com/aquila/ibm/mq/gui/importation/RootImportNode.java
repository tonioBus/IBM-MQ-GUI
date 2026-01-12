package com.aquila.ibm.mq.gui.importation;

import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.model.HierarchyNode;
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
