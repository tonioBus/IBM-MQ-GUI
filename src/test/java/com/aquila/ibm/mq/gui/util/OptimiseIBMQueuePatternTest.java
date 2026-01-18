package com.aquila.ibm.mq.gui.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class OptimiseIBMQueuePatternTest {

    @Test
    void testSimplePattern() {
        String[] queueNames = {"DEV.QUEUE.1.TST.1", "DEV.QUEUE.2.TST.1", "DEV.QUEUE.4.TST.2"};

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testCommonPrefixOnly() {
        String[] queueNames = {"DEV.QUEUE.1", "DEV.QUEUE.2", "DEV.QUEUE.3"};

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: Common Prefix Only ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testCommonPrefixAndSuffix() {
        String[] queueNames = {
            "APP.SERVICE.USER.REQUEST",
            "APP.SERVICE.ORDER.REQUEST",
            "APP.SERVICE.PAYMENT.REQUEST"
        };

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: Common Prefix and Suffix ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testNoCommonPattern() {
        String[] queueNames = {"AAA", "BBB", "CCC"};

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: No Common Pattern ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testSingleQueue() {
        String[] queueNames = {"SINGLE.QUEUE.NAME"};

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: Single Queue ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testSystemQueues() {
        String[] queueNames = {
            "SYSTEM.ADMIN.COMMAND.QUEUE",
            "SYSTEM.ADMIN.CONFIG.EVENT",
            "SYSTEM.ADMIN.CHANNEL.EVENT",
            "SYSTEM.AUTH.DATA.QUEUE",
            "SYSTEM.CLUSTER.COMMAND.QUEUE"
        };

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: System Queues ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testMixedEnvironments() {
        String[] queueNames = {
            "DEV.QUEUE.1",
            "DEV.QUEUE.2",
            "TEST.QUEUE.1",
            "TEST.QUEUE.2",
            "PROD.QUEUE.1"
        };

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: Mixed Environments ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        verifyPatternsMatchAll(patterns, queueNames);
    }

    @Test
    void testEmptyArray() {
        String[] queueNames = {};

        List<String> patterns = createIBMMPattern(queueNames);
        log.info("\n=== Test: Empty Array ===");
        log.info("Input queues: {}", Arrays.toString(queueNames));
        log.info("Optimized IBM MQ patterns: {}", patterns);
        assert patterns.isEmpty();
    }

    /**
     * Verifies that all generated patterns actually match all input queue names.
     */
    private void verifyPatternsMatchAll(List<String> patterns, String[] queueNames) {
        for (String queueName : queueNames) {
            boolean matched = false;
            for (String pattern : patterns) {
                // Use the utility method to convert IBM MQ pattern to regex
                String regex = QueueNameRegexCalculator.convertMQWildcardToRegex(pattern);
                if (queueName.matches(regex)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                log.error("Queue '{}' does not match any pattern: {}", queueName, patterns);
                throw new AssertionError("Queue '" + queueName + "' not matched by any pattern");
            }
        }
        log.info("✓ All {} queues matched by {} pattern(s)", queueNames.length, patterns.size());
    }

    /**
     * Delegates to QueueNameRegexCalculator utility class.
     * Creates optimized IBM MQ wildcard patterns from a list of queue names.
     *
     * @param queueNames Array of queue names to analyze
     * @return List of optimized IBM MQ wildcard patterns
     */
    private List<String> createIBMMPattern(String[] queueNames) {
        return QueueNameRegexCalculator.createOptimizedIBMMQPatterns(queueNames);
    }
}
