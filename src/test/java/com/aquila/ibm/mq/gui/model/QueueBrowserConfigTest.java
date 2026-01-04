package com.aquila.ibm.mq.gui.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class QueueBrowserConfigTest {

    @Test
    void testSerDeser() throws FileNotFoundException {
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final String file = "doc/.ibmmqgui/52f20a1e-f41b-4c1a-a005-ecd04b8ff9e4.json";
        final Reader reader = new FileReader(file);
        final QueueBrowserConfig queueBrowserConfig = gson.fromJson(reader, QueueBrowserConfig.class);
        assertNotNull(queueBrowserConfig);
        log.info("queueBrowserConfig:\n{}",  gson.toJson(queueBrowserConfig));
    }
}