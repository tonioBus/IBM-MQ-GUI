/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Utility for generating regex patterns from queue names.
 * Supports IBM MQ wildcards (* and ?), pattern optimization,
 * and automatic conversion between MQ wildcards and Java regex.
 */
package com.aquila.ibm.mq.gui.util;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for generating regular expression patterns from queue names.
 * Supports creating regex patterns that match a list of queue names,
 * with options for exact matching or wildcard pattern generation.
 */
public class QueueNameRegexCalculator {

    /**
     * Generates a regular expression that matches any of the provided queue names exactly.
     * The pattern is case-insensitive by default.
     *
     * @param queueNames List of queue names to match
     * @return Regular expression pattern string
     * @throws IllegalArgumentException if queueNames is null or empty
     */
    public static String calculateExactMatchRegex(List<String> queueNames) {
        if (queueNames == null || queueNames.isEmpty()) {
            throw new IllegalArgumentException("Queue names list cannot be null or empty");
        }

        // Escape special regex characters and join with OR operator
        String pattern = queueNames.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        return "(" + pattern + ")";
    }

    /**
     * Generates a regular expression that matches queue names with wildcard support.
     * Converts IBM MQ wildcard patterns (* and ?) to regex equivalents.
     *
     * @param queuePatterns List of queue name patterns (may contain * and ? wildcards)
     * @return Regular expression pattern string
     * @throws IllegalArgumentException if queuePatterns is null or empty
     */
    public static String calculateWildcardRegex(List<String> queuePatterns) {
        if (queuePatterns == null || queuePatterns.isEmpty()) {
            throw new IllegalArgumentException("Queue patterns list cannot be null or empty");
        }

        String pattern = queuePatterns.stream()
                .map(QueueNameRegexCalculator::convertWildcardToRegex)
                .collect(Collectors.joining("|"));

        return "(" + pattern + ")";
    }

    /**
     * Generates a regex pattern that matches queue names starting with common prefixes.
     * Useful for finding patterns in queue naming conventions.
     *
     * @param queueNames List of queue names to analyze
     * @return Regular expression pattern based on common prefixes, or exact match if no common pattern
     */
    public static String calculatePrefixBasedRegex(List<String> queueNames) {
        if (queueNames == null || queueNames.isEmpty()) {
            throw new IllegalArgumentException("Queue names list cannot be null or empty");
        }

        if (queueNames.size() == 1) {
            return Pattern.quote(queueNames.get(0));
        }

        String commonPrefix = findCommonPrefix(queueNames);

        if (commonPrefix.isEmpty()) {
            // No common prefix, return exact match pattern
            return calculateExactMatchRegex(queueNames);
        }

        // Create pattern: common prefix + any characters
        return Pattern.quote(commonPrefix) + ".*";
    }

    /**
     * Generates an optimized regex that tries to find common patterns among queue names.
     * This method analyzes the queue names and creates a more compact regex pattern.
     *
     * @param queueNames List of queue names to analyze
     * @return Optimized regular expression pattern
     */
    public static String calculateOptimizedRegex(List<String> queueNames) {
        if (queueNames == null || queueNames.isEmpty()) {
            throw new IllegalArgumentException("Queue names list cannot be null or empty");
        }

        if (queueNames.size() == 1) {
            return Pattern.quote(queueNames.get(0));
        }

        // Find common prefix
        String commonPrefix = findCommonPrefix(queueNames);

        if (!commonPrefix.isEmpty()) {
            // Remove common prefix from all names
            List<String> suffixes = queueNames.stream()
                    .map(name -> name.substring(commonPrefix.length()))
                    .collect(Collectors.toList());

            // Find common suffix
            String commonSuffix = findCommonSuffix(suffixes);

            if (!commonSuffix.isEmpty()) {
                // Remove common suffix
                List<String> middles = suffixes.stream()
                        .map(suffix -> suffix.substring(0, suffix.length() - commonSuffix.length()))
                        .collect(Collectors.toList());

                // Build optimized pattern
                String middlePattern = middles.stream()
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"));

                return Pattern.quote(commonPrefix) + "(" + middlePattern + ")" + Pattern.quote(commonSuffix);
            } else {
                // Only common prefix
                String suffixPattern = suffixes.stream()
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"));

                return Pattern.quote(commonPrefix) + "(" + suffixPattern + ")";
            }
        }

        // No optimization possible, return exact match
        return calculateExactMatchRegex(queueNames);
    }

    /**
     * Compiles a regex pattern from queue names and returns a Pattern object ready for matching.
     *
     * @param queueNames List of queue names
     * @param caseInsensitive Whether the pattern should be case-insensitive
     * @return Compiled Pattern object
     */
    public static Pattern compilePattern(List<String> queueNames, boolean caseInsensitive) {
        String regex = calculateExactMatchRegex(queueNames);
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        return Pattern.compile(regex, flags);
    }

    /**
     * Smart pattern compiler that auto-detects and converts IBM MQ wildcards to Java regex.
     * Supports both IBM MQ wildcard patterns (*, ?) and full Java regex syntax.
     *
     * If the pattern looks like a simple IBM MQ wildcard (only contains * and ? without
     * regex special characters), it's automatically converted to proper regex.
     * Otherwise, it's treated as a Java regex pattern.
     *
     * @param userPattern User-provided pattern (can be IBM MQ wildcard or Java regex)
     * @param caseInsensitive Whether the pattern should be case-insensitive
     * @return Compiled Pattern object
     * @throws java.util.regex.PatternSyntaxException if the pattern is invalid regex
     */
    public static Pattern compileSmartPattern(String userPattern, boolean caseInsensitive) {
        if (userPattern == null || userPattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }

        String regexPattern;

        // Detect if this looks like an IBM MQ wildcard pattern
        if (isIBMMQWildcardPattern(userPattern)) {
            // Convert IBM MQ wildcard to regex
            regexPattern = convertWildcardToRegex(userPattern);
        } else {
            // Use as-is (assume it's already a Java regex)
            regexPattern = userPattern;
        }

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        return Pattern.compile(regexPattern, flags);
    }

    /**
     * Detects if a pattern looks like an IBM MQ wildcard pattern rather than Java regex.
     * A pattern is considered an IBM MQ wildcard if it:
     * - Contains * or ? characters
     * - Does NOT contain typical regex special characters like [], (), {}, +, ^, $, |, \
     *
     * @param pattern Pattern to check
     * @return true if it looks like an IBM MQ wildcard pattern
     */
    public static boolean isIBMMQWildcardPattern(String pattern) {
        // Must contain wildcard characters
        boolean hasWildcards = pattern.contains("*") || pattern.contains("?");

        // Should not contain regex-specific characters (excluding . which is used in queue names)
        boolean hasRegexChars = pattern.matches(".*[\\[\\](){}+^$|\\\\].*");

        return hasWildcards && !hasRegexChars;
    }

    /**
     * Converts IBM MQ wildcard pattern to Java regex pattern string (public version).
     * - * becomes .*
     * - ? becomes .
     * - All other characters are properly escaped
     * - Pattern is anchored with ^ and $
     *
     * @param wildcardPattern IBM MQ wildcard pattern (e.g., "DEV.*", "QUEUE.?")
     * @return Java regex pattern string
     */
    public static String convertMQWildcardToRegex(String wildcardPattern) {
        return convertWildcardToRegex(wildcardPattern);
    }

    /**
     * Creates optimized IBM MQ wildcard patterns from a list of queue names.
     * Uses * (matches any chars) and ? (matches single char) wildcards.
     * Returns the minimal set of patterns that cover all input queues.
     *
     * Examples:
     * - ["DEV.QUEUE.1", "DEV.QUEUE.2", "DEV.QUEUE.3"] → ["DEV.QUEUE.*"]
     * - ["SYSTEM.ADMIN.COMMAND.QUEUE", "SYSTEM.AUTH.DATA.QUEUE"] → ["SYSTEM.*"]
     * - ["DEV.QUEUE.1", "TEST.QUEUE.1", "PROD.QUEUE.1"] → ["DEV.QUEUE*", "TEST.QUEUE*", "PROD.QUEUE*"]
     *
     * @param queueNames Array of queue names to analyze
     * @return List of optimized IBM MQ wildcard patterns
     */
    public static List<String> createOptimizedIBMMQPatterns(String[] queueNames) {
        if (queueNames == null || queueNames.length == 0) {
            return Collections.emptyList();
        }

        // Single queue - return exact name
        if (queueNames.length == 1) {
            return Collections.singletonList(queueNames[0]);
        }

        List<String> patterns = new ArrayList<>();
        List<String> namesList = Arrays.asList(queueNames);

        // Strategy 1: Try to find a single common prefix pattern
        String commonPrefix = findCommonPrefix(namesList);
        if (!commonPrefix.isEmpty()) {
            // Check if we can use a simple prefix wildcard
            String prefixPattern = commonPrefix + "*";
            if (allMatchIBMMQPattern(namesList, prefixPattern)) {
                patterns.add(prefixPattern);
                return patterns;
            }
        }

        // Strategy 2: Group by common prefix and suffix, use wildcards for middle parts
        Map<String, List<String>> groupedByPrefixSuffix = groupByPrefixSuffix(namesList);
        for (Map.Entry<String, List<String>> entry : groupedByPrefixSuffix.entrySet()) {
            String pattern = entry.getKey();
            patterns.add(pattern);
        }

        // If grouping resulted in too many patterns, fall back to simple prefix wildcards
        if (patterns.size() > namesList.size() / 2) {
            patterns.clear();
            // Group by common prefixes and create patterns
            Map<String, List<String>> prefixGroups = groupByCommonPrefix(namesList);
            for (Map.Entry<String, List<String>> entry : prefixGroups.entrySet()) {
                patterns.add(entry.getKey() + "*");
            }
        }

        return patterns;
    }

    /**
     * Creates optimized IBM MQ wildcard patterns from a list of queue names.
     * Overload that accepts List<String> instead of String[].
     *
     * @param queueNames List of queue names to analyze
     * @return List of optimized IBM MQ wildcard patterns
     */
    public static List<String> createOptimizedIBMMQPatterns(List<String> queueNames) {
        if (queueNames == null || queueNames.isEmpty()) {
            return Collections.emptyList();
        }
        return createOptimizedIBMMQPatterns(queueNames.toArray(new String[0]));
    }

    /**
     * Groups queue names by common prefix and suffix, creating optimized patterns.
     *
     * @param names List of queue names
     * @return Map of pattern to matching queue names
     */
    private static Map<String, List<String>> groupByPrefixSuffix(List<String> names) {
        Map<String, List<String>> groups = new HashMap<>();

        // Find common prefix
        String prefix = findCommonPrefix(names);

        // Remove prefix from all names
        List<String> withoutPrefix = names.stream()
                .map(name -> name.substring(prefix.length()))
                .collect(Collectors.toList());

        // Find common suffix
        String suffix = findCommonSuffix(withoutPrefix);

        if (!suffix.isEmpty()) {
            // Pattern: prefix + * + suffix
            String pattern = prefix + "*" + suffix;
            groups.put(pattern, names);
        } else if (!prefix.isEmpty()) {
            // Pattern: prefix + *
            String pattern = prefix + "*";
            groups.put(pattern, names);
        } else {
            // No common pattern - try to group by first segment
            Map<String, List<String>> segmentGroups = new HashMap<>();
            for (String name : names) {
                String firstSegment = name.split("\\.")[0];
                segmentGroups.computeIfAbsent(firstSegment, k -> new ArrayList<>()).add(name);
            }

            // Create patterns for each group
            for (Map.Entry<String, List<String>> entry : segmentGroups.entrySet()) {
                String groupPrefix = findCommonPrefix(entry.getValue());
                if (!groupPrefix.isEmpty()) {
                    groups.put(groupPrefix + "*", entry.getValue());
                } else {
                    // Worst case - use exact names
                    for (String name : entry.getValue()) {
                        groups.put(name, Collections.singletonList(name));
                    }
                }
            }
        }

        return groups;
    }

    /**
     * Groups queue names by their common prefix (up to the last common character).
     *
     * @param names List of queue names
     * @return Map of prefix to matching queue names
     */
    private static Map<String, List<String>> groupByCommonPrefix(List<String> names) {
        Map<String, List<String>> groups = new HashMap<>();

        // Simple grouping: take first 2 segments as prefix
        for (String name : names) {
            String[] parts = name.split("\\.");
            String prefix = parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
            groups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
        }

        return groups;
    }

    /**
     * Checks if all names match the given IBM MQ wildcard pattern.
     *
     * @param names List of queue names to check
     * @param mqPattern IBM MQ wildcard pattern
     * @return true if all names match the pattern
     */
    private static boolean allMatchIBMMQPattern(List<String> names, String mqPattern) {
        String regex = convertWildcardToRegex(mqPattern);
        Pattern pattern = Pattern.compile(regex);
        for (String name : names) {
            if (!pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts an IBM MQ wildcard pattern to a regular expression.
     * - * becomes .*
     * - ? becomes .
     * - Other characters are escaped
     *
     * @param wildcardPattern Wildcard pattern (e.g., "QUEUE.*", "TEST?")
     * @return Regular expression equivalent
     */
    private static String convertWildcardToRegex(String wildcardPattern) {
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < wildcardPattern.length(); i++) {
            char c = wildcardPattern.charAt(i);

            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                default:
                    // Escape special regex characters
                    String escaped = Pattern.quote(String.valueOf(c));
                    regex.append(escaped);
                    break;
            }
        }

        regex.append("$");
        return regex.toString();
    }

    /**
     * Finds the longest common prefix among a list of strings.
     *
     * @param strings List of strings to analyze
     * @return Common prefix, or empty string if none found
     */
    private static String findCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }

        String first = strings.get(0);
        int prefixLength = first.length();

        for (String str : strings) {
            prefixLength = Math.min(prefixLength, str.length());
            for (int i = 0; i < prefixLength; i++) {
                if (first.charAt(i) != str.charAt(i)) {
                    prefixLength = i;
                    break;
                }
            }
        }

        return first.substring(0, prefixLength);
    }

    /**
     * Finds the longest common suffix among a list of strings.
     *
     * @param strings List of strings to analyze
     * @return Common suffix, or empty string if none found
     */
    private static String findCommonSuffix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }

        String first = strings.get(0);
        int suffixLength = first.length();

        for (String str : strings) {
            suffixLength = Math.min(suffixLength, str.length());
            for (int i = 0; i < suffixLength; i++) {
                if (first.charAt(first.length() - 1 - i) != str.charAt(str.length() - 1 - i)) {
                    suffixLength = i;
                    break;
                }
            }
        }

        return first.substring(first.length() - suffixLength);
    }
}
