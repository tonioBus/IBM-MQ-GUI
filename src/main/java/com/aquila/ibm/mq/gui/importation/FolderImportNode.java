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
