package com.aquila.ibm.mq.gui.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class QueueNameRegexCalculatorTest {

    // ========== calculateExactMatchRegex Tests ==========

    @Test
    void testCalculateExactMatchRegex_SingleQueue() {
        List<String> queues = Collections.singletonList("QUEUE.ONE");
        String regex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);

        log.info("Single queue regex: {}", regex);
        // Pattern.quote wraps the string in \Q and \E
        assertTrue(regex.contains("QUEUE.ONE"));

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertFalse(pattern.matcher("QUEUE.TWO").find());
    }

    @Test
    void testCalculateExactMatchRegex_MultipleQueues() {
        List<String> queues = Arrays.asList("QUEUE.ONE", "QUEUE.TWO", "QUEUE.THREE");
        String regex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);

        log.info("Multiple queues regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("QUEUE.TWO").find());
        assertTrue(pattern.matcher("QUEUE.THREE").find());
        assertFalse(pattern.matcher("QUEUE.FOUR").find());
    }

    @Test
    void testCalculateExactMatchRegex_SpecialCharacters() {
        List<String> queues = Arrays.asList("QUEUE.TEST*", "QUEUE.TEST?", "QUEUE.TEST[1]");
        String regex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);

        log.info("Special characters regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.TEST*").find());
        assertTrue(pattern.matcher("QUEUE.TEST?").find());
        assertTrue(pattern.matcher("QUEUE.TEST[1]").find());
        assertFalse(pattern.matcher("QUEUE.TESTX").find());
    }

    @Test
    void testCalculateExactMatchRegex_NullList() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            QueueNameRegexCalculator.calculateExactMatchRegex(null);
        });

        assertEquals("Queue names list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testCalculateExactMatchRegex_EmptyList() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            QueueNameRegexCalculator.calculateExactMatchRegex(Collections.emptyList());
        });

        assertEquals("Queue names list cannot be null or empty", exception.getMessage());
    }

    // ========== calculateWildcardRegex Tests ==========

    @Test
    void testCalculateWildcardRegex_AsteriskWildcard() {
        List<String> patterns = Collections.singletonList("QUEUE.*");
        String regex = QueueNameRegexCalculator.calculateWildcardRegex(patterns);

        log.info("Asterisk wildcard regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("QUEUE.TWO").find());
        assertTrue(pattern.matcher("QUEUE.").find());
        assertFalse(pattern.matcher("QUEUEONE").find());
    }

    @Test
    void testCalculateWildcardRegex_QuestionMarkWildcard() {
        List<String> patterns = Collections.singletonList("QUEUE.?");
        String regex = QueueNameRegexCalculator.calculateWildcardRegex(patterns);

        log.info("Question mark wildcard regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.1").find());
        assertTrue(pattern.matcher("QUEUE.A").find());
        assertFalse(pattern.matcher("QUEUE.12").find());
        assertFalse(pattern.matcher("QUEUE.").find());
    }

    @Test
    void testCalculateWildcardRegex_MixedWildcards() {
        List<String> patterns = Arrays.asList("DEV.*", "TEST.?", "PROD.QUEUE");
        String regex = QueueNameRegexCalculator.calculateWildcardRegex(patterns);

        log.info("Mixed wildcards regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("DEV.QUEUE.ONE").find());
        assertTrue(pattern.matcher("TEST.A").find());
        assertTrue(pattern.matcher("PROD.QUEUE").find());
        assertFalse(pattern.matcher("UAT.QUEUE").find());
    }

    @Test
    void testCalculateWildcardRegex_ComplexPattern() {
        List<String> patterns = Collections.singletonList("QUEUE.*.TEST.?");
        String regex = QueueNameRegexCalculator.calculateWildcardRegex(patterns);

        log.info("Complex wildcard regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.DEV.TEST.1").find());
        assertTrue(pattern.matcher("QUEUE.PRODUCTION.TEST.A").find());
        assertFalse(pattern.matcher("QUEUE.DEV.TEST.12").find());
    }

    @Test
    void testCalculateWildcardRegex_NullList() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            QueueNameRegexCalculator.calculateWildcardRegex(null);
        });

        assertEquals("Queue patterns list cannot be null or empty", exception.getMessage());
    }

    // ========== calculatePrefixBasedRegex Tests ==========

    @Test
    void testCalculatePrefixBasedRegex_CommonPrefix() {
        List<String> queues = Arrays.asList("QUEUE.ONE", "QUEUE.TWO", "QUEUE.THREE");
        String regex = QueueNameRegexCalculator.calculatePrefixBasedRegex(queues);

        log.info("Common prefix regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("QUEUE.FOUR").find());
        assertTrue(pattern.matcher("QUEUE.ANY").find());
        assertFalse(pattern.matcher("TOPIC.ONE").find());
    }

    @Test
    void testCalculatePrefixBasedRegex_NoCommonPrefix() {
        List<String> queues = Arrays.asList("QUEUE.ONE", "TOPIC.TWO", "ALIAS.THREE");
        String regex = QueueNameRegexCalculator.calculatePrefixBasedRegex(queues);

        log.info("No common prefix regex: {}", regex);

        // Should fall back to exact match
        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("TOPIC.TWO").find());
        assertTrue(pattern.matcher("ALIAS.THREE").find());
        assertFalse(pattern.matcher("QUEUE.TWO").find());
    }

    @Test
    void testCalculatePrefixBasedRegex_SingleQueue() {
        List<String> queues = Collections.singletonList("QUEUE.SINGLE");
        String regex = QueueNameRegexCalculator.calculatePrefixBasedRegex(queues);

        log.info("Single queue prefix regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.SINGLE").find());
        // Single queue returns exact match (using Pattern.quote), not a wildcard pattern
        // So it should NOT match extra text - this is the correct behavior
        assertFalse(pattern.matcher("QUEUE.OTHER").find());
    }

    // ========== calculateOptimizedRegex Tests ==========

    @Test
    void testCalculateOptimizedRegex_PrefixAndSuffix() {
        List<String> queues = Arrays.asList("QUEUE.DEV.LOG", "QUEUE.TEST.LOG", "QUEUE.PROD.LOG");
        String regex = QueueNameRegexCalculator.calculateOptimizedRegex(queues);

        log.info("Optimized regex with prefix and suffix: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.DEV.LOG").find());
        assertTrue(pattern.matcher("QUEUE.TEST.LOG").find());
        assertTrue(pattern.matcher("QUEUE.PROD.LOG").find());
        assertFalse(pattern.matcher("QUEUE.UAT.LOG").find());
        assertFalse(pattern.matcher("QUEUE.DEV.ERROR").find());
    }

    @Test
    void testCalculateOptimizedRegex_PrefixOnly() {
        List<String> queues = Arrays.asList("QUEUE.ONE", "QUEUE.TWO", "QUEUE.THREE");
        String regex = QueueNameRegexCalculator.calculateOptimizedRegex(queues);

        log.info("Optimized regex with prefix only: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("QUEUE.TWO").find());
        assertTrue(pattern.matcher("QUEUE.THREE").find());
        assertFalse(pattern.matcher("QUEUE.FOUR").find());
    }

    @Test
    void testCalculateOptimizedRegex_NoPattern() {
        List<String> queues = Arrays.asList("AAA", "BBB", "CCC");
        String regex = QueueNameRegexCalculator.calculateOptimizedRegex(queues);

        log.info("Optimized regex with no pattern: {}", regex);

        // Should fall back to exact match
        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("AAA").find());
        assertTrue(pattern.matcher("BBB").find());
        assertTrue(pattern.matcher("CCC").find());
        assertFalse(pattern.matcher("DDD").find());
    }

    @Test
    void testCalculateOptimizedRegex_SingleQueue() {
        List<String> queues = Collections.singletonList("SINGLE.QUEUE");
        String regex = QueueNameRegexCalculator.calculateOptimizedRegex(queues);

        log.info("Optimized single queue regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("SINGLE.QUEUE").find());
        // Single queue returns exact match (using Pattern.quote)
        assertFalse(pattern.matcher("OTHER.QUEUE").find());
    }

    @Test
    void testCalculateOptimizedRegex_ComplexPattern() {
        List<String> queues = Arrays.asList(
            "APP.SERVICE.USER.REQUEST",
            "APP.SERVICE.ORDER.REQUEST",
            "APP.SERVICE.PAYMENT.REQUEST"
        );
        String regex = QueueNameRegexCalculator.calculateOptimizedRegex(queues);

        log.info("Complex optimized regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("APP.SERVICE.USER.REQUEST").find());
        assertTrue(pattern.matcher("APP.SERVICE.ORDER.REQUEST").find());
        assertTrue(pattern.matcher("APP.SERVICE.PAYMENT.REQUEST").find());
        assertFalse(pattern.matcher("APP.SERVICE.PRODUCT.REQUEST").find());
    }

    @Test
    void testCalculateOptimizedRegex_NullList() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            QueueNameRegexCalculator.calculateOptimizedRegex(null);
        });

        assertEquals("Queue names list cannot be null or empty", exception.getMessage());
    }

    // ========== compilePattern Tests ==========

    @Test
    void testCompilePattern_CaseSensitive() {
        List<String> queues = Arrays.asList("queue.one", "queue.two");
        Pattern pattern = QueueNameRegexCalculator.compilePattern(queues, false);

        assertTrue(pattern.matcher("queue.one").find());
        assertFalse(pattern.matcher("QUEUE.ONE").find());
    }

    @Test
    void testCompilePattern_CaseInsensitive() {
        List<String> queues = Arrays.asList("queue.one", "queue.two");
        Pattern pattern = QueueNameRegexCalculator.compilePattern(queues, true);

        assertTrue(pattern.matcher("queue.one").find());
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("Queue.One").find());
    }

    // ========== Integration Tests ==========

    @Test
    void testRealWorldScenario_IBMMQQueues() {
        List<String> queues = Arrays.asList(
            "SYSTEM.ADMIN.COMMAND.QUEUE",
            "SYSTEM.AUTH.DATA.QUEUE",
            "SYSTEM.CHANNEL.INITQ",
            "DEV.QUEUE.1",
            "DEV.QUEUE.2",
            "PROD.QUEUE.1"
        );

        // Test exact match
        String exactRegex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);
        Pattern exactPattern = Pattern.compile(exactRegex);
        assertTrue(exactPattern.matcher("SYSTEM.ADMIN.COMMAND.QUEUE").find());
        assertFalse(exactPattern.matcher("SYSTEM.UNKNOWN.QUEUE").find());

        // Test optimized for subset with common prefix
        List<String> devQueues = Arrays.asList("DEV.QUEUE.1", "DEV.QUEUE.2");
        String optimizedRegex = QueueNameRegexCalculator.calculateOptimizedRegex(devQueues);
        Pattern optimizedPattern = Pattern.compile(optimizedRegex);
        assertTrue(optimizedPattern.matcher("DEV.QUEUE.1").find());
        assertTrue(optimizedPattern.matcher("DEV.QUEUE.2").find());
        assertFalse(optimizedPattern.matcher("DEV.QUEUE.3").find());

        log.info("Real-world exact regex: {}", exactRegex);
        log.info("Real-world optimized regex: {}", optimizedRegex);
    }

    @Test
    void testRealWorldScenario_WildcardPatterns() {
        List<String> patterns = Arrays.asList("DEV.*", "TEST.*", "SYSTEM.*.QUEUE");
        String regex = QueueNameRegexCalculator.calculateWildcardRegex(patterns);
        Pattern pattern = Pattern.compile(regex);

        assertTrue(pattern.matcher("DEV.QUEUE.1").find());
        assertTrue(pattern.matcher("TEST.APPLICATION").find());
        assertTrue(pattern.matcher("SYSTEM.ADMIN.QUEUE").find());
        assertFalse(pattern.matcher("PROD.QUEUE.1").find());

        log.info("Real-world wildcard regex: {}", regex);
    }

    @Test
    void testEdgeCase_EmptyStrings() {
        List<String> queues = Arrays.asList("", "QUEUE.ONE");
        String regex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);
        Pattern pattern = Pattern.compile(regex);

        assertTrue(pattern.matcher("").find());
        assertTrue(pattern.matcher("QUEUE.ONE").find());
    }

    @Test
    void testEdgeCase_VeryLongQueueName() {
        String longName = "VERY.LONG.QUEUE.NAME.WITH.MANY.SEGMENTS.THAT.GOES.ON.AND.ON";
        List<String> queues = Collections.singletonList(longName);
        String regex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);
        Pattern pattern = Pattern.compile(regex);

        assertTrue(pattern.matcher(longName).find());
    }

    @Test
    void testEdgeCase_SpecialRegexCharacters() {
        List<String> queues = Arrays.asList(
            "QUEUE.(TEST)",
            "QUEUE.[TEST]",
            "QUEUE.{TEST}",
            "QUEUE.TEST+",
            "QUEUE.TEST$"
        );
        String regex = QueueNameRegexCalculator.calculateExactMatchRegex(queues);
        Pattern pattern = Pattern.compile(regex);

        assertTrue(pattern.matcher("QUEUE.(TEST)").find());
        assertTrue(pattern.matcher("QUEUE.[TEST]").find());
        assertTrue(pattern.matcher("QUEUE.{TEST}").find());
        assertTrue(pattern.matcher("QUEUE.TEST+").find());
        assertTrue(pattern.matcher("QUEUE.TEST$").find());
    }

    // ========== Smart Pattern Compilation Tests ==========

    @Test
    void testIsIBMMQWildcardPattern_SimpleWildcards() {
        assertTrue(QueueNameRegexCalculator.isIBMMQWildcardPattern("DEV.*"));
        assertTrue(QueueNameRegexCalculator.isIBMMQWildcardPattern("QUEUE.?"));
        assertTrue(QueueNameRegexCalculator.isIBMMQWildcardPattern("TEST.*.LOG"));
        assertTrue(QueueNameRegexCalculator.isIBMMQWildcardPattern("*"));
        assertTrue(QueueNameRegexCalculator.isIBMMQWildcardPattern("SYSTEM.?.QUEUE"));
    }

    @Test
    void testIsIBMMQWildcardPattern_JavaRegex() {
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("QUEUE\\.(ONE|TWO)"));
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("DEV\\..*"));
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("^QUEUE.*$"));
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("QUEUE[0-9]+"));
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("TEST{3}"));
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("A|B"));
    }

    @Test
    void testIsIBMMQWildcardPattern_NoWildcards() {
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("QUEUE.ONE"));
        assertFalse(QueueNameRegexCalculator.isIBMMQWildcardPattern("SIMPLE.QUEUE.NAME"));
    }

    @Test
    void testCompileSmartPattern_IBMMQWildcards() {
        // Test asterisk wildcard
        Pattern pattern = QueueNameRegexCalculator.compileSmartPattern("DEV.*", true);
        assertTrue(pattern.matcher("DEV.QUEUE.ONE").matches());
        assertTrue(pattern.matcher("DEV.TEST").matches());
        assertFalse(pattern.matcher("PROD.QUEUE").matches());

        // Test question mark wildcard
        pattern = QueueNameRegexCalculator.compileSmartPattern("QUEUE.?", false);
        assertTrue(pattern.matcher("QUEUE.1").matches());
        assertTrue(pattern.matcher("QUEUE.A").matches());
        assertFalse(pattern.matcher("QUEUE.12").matches());

        // Test mixed wildcards
        pattern = QueueNameRegexCalculator.compileSmartPattern("SYSTEM.*.?", true);
        assertTrue(pattern.matcher("SYSTEM.ADMIN.Q").matches());
        assertTrue(pattern.matcher("SYSTEM.TEST.1").matches());
        assertFalse(pattern.matcher("SYSTEM.ADMIN.QUEUE").matches());
    }

    @Test
    void testCompileSmartPattern_JavaRegex() {
        // Test alternation
        Pattern pattern = QueueNameRegexCalculator.compileSmartPattern("QUEUE\\.(ONE|TWO)", true);
        assertTrue(pattern.matcher("QUEUE.ONE").find());
        assertTrue(pattern.matcher("QUEUE.TWO").find());
        assertFalse(pattern.matcher("QUEUE.THREE").find());

        // Test character class
        pattern = QueueNameRegexCalculator.compileSmartPattern("QUEUE\\.[0-9]+", false);
        assertTrue(pattern.matcher("QUEUE.123").find());
        assertTrue(pattern.matcher("QUEUE.1").find());
        assertFalse(pattern.matcher("QUEUE.ABC").find());
    }

    @Test
    void testCompileSmartPattern_CaseInsensitive() {
        Pattern pattern = QueueNameRegexCalculator.compileSmartPattern("dev.*", true);
        assertTrue(pattern.matcher("DEV.QUEUE").matches());
        assertTrue(pattern.matcher("dev.queue").matches());
        assertTrue(pattern.matcher("Dev.Queue").matches());
    }

    @Test
    void testCompileSmartPattern_CaseSensitive() {
        Pattern pattern = QueueNameRegexCalculator.compileSmartPattern("DEV.*", false);
        assertTrue(pattern.matcher("DEV.QUEUE").matches());
        assertFalse(pattern.matcher("dev.queue").matches());
    }

    @Test
    void testCompileSmartPattern_NullPattern() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            QueueNameRegexCalculator.compileSmartPattern(null, true);
        });
        assertEquals("Pattern cannot be null or empty", exception.getMessage());
    }

    @Test
    void testCompileSmartPattern_EmptyPattern() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            QueueNameRegexCalculator.compileSmartPattern("", true);
        });
        assertEquals("Pattern cannot be null or empty", exception.getMessage());
    }

    @Test
    void testConvertMQWildcardToRegex() {
        String regex = QueueNameRegexCalculator.convertMQWildcardToRegex("DEV.*");
        log.info("Converted MQ wildcard 'DEV.*' to regex: {}", regex);

        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher("DEV.QUEUE").matches());
        assertTrue(pattern.matcher("DEV.TEST.QUEUE").matches());
        assertFalse(pattern.matcher("PROD.QUEUE").matches());
    }

    @Test
    void testSmartPattern_RealWorldUsage() {
        // Simulate user typing different patterns

        // User types simple wildcard - should auto-convert
        Pattern p1 = QueueNameRegexCalculator.compileSmartPattern("DEV.*", true);
        assertTrue(p1.matcher("DEV.QUEUE.1").matches());

        // User types complex regex - should use as-is
        Pattern p2 = QueueNameRegexCalculator.compileSmartPattern("(DEV|TEST)\\.QUEUE.*", true);
        assertTrue(p2.matcher("DEV.QUEUE.1").find());
        assertTrue(p2.matcher("TEST.QUEUE.1").find());

        // User types exact name (no wildcards) - should match literally
        Pattern p3 = QueueNameRegexCalculator.compileSmartPattern("EXACT.QUEUE.NAME", true);
        assertTrue(p3.matcher("EXACT.QUEUE.NAME").find());
        assertFalse(p3.matcher("EXACT.QUEUE.NAME.EXTRA").matches());
    }
}
