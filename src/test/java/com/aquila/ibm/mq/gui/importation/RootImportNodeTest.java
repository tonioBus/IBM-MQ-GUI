package com.aquila.ibm.mq.gui.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class RootImportNodeTest {

    @Test
    void testImport() throws IOException {
        final File file = new File("doc/import.json");
        final RootImportNode rootImportNode = RootImportNode.from(file);
        log.info("rootImportNode:\n{}", rootImportNode);
    }
}