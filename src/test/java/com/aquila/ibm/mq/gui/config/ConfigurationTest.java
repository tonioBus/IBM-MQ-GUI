package com.aquila.ibm.mq.gui.config;

import com.aquila.ibm.mq.gui.model.QueueManagerConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.MQQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ConfigurationTest {

    @Test
    void loadConnections() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(Objects.requireNonNull(this.getClass().getClassLoader().getResource("connections.json")).getFile());
        JavaType javaType = objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, QueueManagerConfig.class);
        Map<String, QueueManagerConfig> a = objectMapper.readValue(file, javaType);
        log.info(" -> {}", a.values().stream().findFirst().get());
    }
}