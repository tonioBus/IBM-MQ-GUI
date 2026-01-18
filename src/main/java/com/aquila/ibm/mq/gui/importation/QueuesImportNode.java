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
