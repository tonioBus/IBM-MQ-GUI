package com.aquila.ibm.mq.gui.model;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Map;

@Slf4j
@Getter
@Builder
public class QueueBrowserConfig {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static QueueBrowserConfig fromFile(String key, String queueManager) {
        final File file = new File(ConfigManager.CONFIG_DIR, key);
        try (Reader reader = new FileReader(file)) {
            final QueueBrowserConfig queueBrowserConfig = gson.fromJson(reader, QueueBrowserConfig.class);
            log.info("queueBrowserConfig:\n{}", gson.toJson(queueBrowserConfig));
            return queueBrowserConfig;
        } catch (IOException e) {
            log.error("Failed to load QueueBrowserConfig: {}", key, e);
            QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.builder()
                    .inspect(true)
                    .queueManager(queueManager)
                    .build();
            try (Writer writer = new FileWriter(file)) {
                gson.toJson(queueBrowserConfig, writer);
                log.info("Saved queueBrowserConfig: {}", file);
            } catch (IOException e1) {
                log.error("Failed to save queueBrowserConfig", e1);
            }
            return queueBrowserConfig;
        }
    }

    record QueueDescription(String label) {
    }

    private String label;
    private String queueManager;
    private boolean inspect;
    private Map<String, QueueDescription> descriptions;
}
