package com.aquila.ibm.mq.gui.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template processor for message content.
 * Supports variable substitution using ${variable} syntax.
 */
public class TemplateProcessor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int sequenceNumber = 0;
    private int totalCount = 1;

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String process(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String variable = matcher.group(1);
            String replacement = resolveVariable(variable);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public static boolean containsVariables(String template) {
        if (template == null || template.isEmpty()) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(template).find();
    }

    public static String getHelpText() {
        return """
            Template Variables:

            Date/Time:
              ${timestamp}    - ISO timestamp (2024-01-15T10:30:45)
              ${date}         - Current date (2024-01-15)
              ${time}         - Current time (10:30:45)
              ${datetime}     - Date and time (2024-01-15 10:30:45)

            Unique Values:
              ${uuid}         - Random UUID
              ${random}       - Random number (0-999999)
              ${random:1:100} - Random number in range

            Batch Variables:
              ${seq}          - Sequence number (1-based)
              ${index}        - Index (0-based)
              ${count}        - Total message count

            System:
              ${hostname}     - Machine hostname
              ${user}         - Current username
              ${env:VAR}      - Environment variable
            """;
    }

    private String resolveVariable(String variable) {
        if (variable.startsWith("random:")) {
            return resolveRandomRange(variable);
        }
        if (variable.startsWith("env:")) {
            return resolveEnvVariable(variable);
        }

        return switch (variable.toLowerCase()) {
            case "timestamp" -> LocalDateTime.now().format(ISO_FORMATTER);
            case "date" -> LocalDate.now().format(DATE_FORMATTER);
            case "time" -> LocalTime.now().format(TIME_FORMATTER);
            case "datetime" -> LocalDateTime.now().format(DATETIME_FORMATTER);
            case "uuid" -> UUID.randomUUID().toString();
            case "random" -> String.valueOf(ThreadLocalRandom.current().nextInt(1000000));
            case "sequence", "seq" -> String.valueOf(sequenceNumber + 1);
            case "index" -> String.valueOf(sequenceNumber);
            case "count" -> String.valueOf(totalCount);
            case "hostname" -> getHostname();
            case "user" -> System.getProperty("user.name", "unknown");
            default -> "${" + variable + "}";
        };
    }

    private String resolveRandomRange(String variable) {
        try {
            String[] parts = variable.split(":");
            if (parts.length == 3) {
                int min = Integer.parseInt(parts[1]);
                int max = Integer.parseInt(parts[2]);
                if (min <= max) {
                    return String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return "${" + variable + "}";
    }

    private String resolveEnvVariable(String variable) {
        String envName = variable.substring(4);
        String value = System.getenv(envName);
        return value != null ? value : "";
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
