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
        final String file = "doc/41f85a24-11ca-42d9-8a9d-59c539d158c5.json";
        final Reader reader = new FileReader(file);
        final QueueBrowserConfig queueBrowserConfig = gson.fromJson(reader, QueueBrowserConfig.class);
        assertNotNull(queueBrowserConfig);
        log.info("queueBrowserConfig:\n{}",  gson.toJson(queueBrowserConfig));
    }
}