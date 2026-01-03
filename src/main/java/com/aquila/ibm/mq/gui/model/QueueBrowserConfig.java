package com.aquila.ibm.mq.gui.model;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Map;

@Slf4j
@Getter
@ToString
@Builder
public class QueueBrowserConfig {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    record QueueDescription(String label, String queue) {
    }

    private String label;
    private String queueManager;
    private String regularExpression;
    private Map<String, QueueDescription> descriptions;

    public static QueueBrowserConfig fromFile(String key, String queueManager) {
        final File file = new File(ConfigManager.CONFIG_DIR, String.format("%s.json", key));
        try (Reader reader = new FileReader(file)) {
            final QueueBrowserConfig queueBrowserConfig = gson.fromJson(reader, QueueBrowserConfig.class);
            log.info("queueBrowserConfig:\n{}", gson.toJson(queueBrowserConfig));
            return queueBrowserConfig;
        } catch (IOException e) {
            log.error("Failed to load QueueBrowserConfig: {}", key, e);
            QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.builder()
                    .queueManager(queueManager)
                    .regularExpression("*")
                    .label("Default")
                    .build();
            queueBrowserConfig.save(key);
            return queueBrowserConfig;
        }
    }

    public void save(String key) {
        String filename = String.format("%s.json", key);
        final File file = new File(ConfigManager.CONFIG_DIR, filename);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(this, writer);
            log.info("Saved queueBrowserConfig: {}", file);
        } catch (IOException e1) {
            log.error("Failed to save queueBrowserConfig", e1);
        }
    }

}
