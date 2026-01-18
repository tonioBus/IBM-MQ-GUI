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
