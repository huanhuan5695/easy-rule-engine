package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PatternExpander {
    private PatternExpander() {
    }

    static List<String> expand(String pattern, int maxExpandedPatterns) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern is required");
        }
        if (maxExpandedPatterns <= 0) {
            throw new IllegalArgumentException("maxExpandedPatterns must be positive");
        }

        List<List<String>> segments = parseSegments(pattern);
        List<String> results = new ArrayList<>();
        results.add("");
        for (List<String> alternatives : segments) {
            List<String> next = new ArrayList<>();
            for (String prefix : results) {
                for (String alternative : alternatives) {
                    next.add(prefix + alternative);
                }
            }
            results = next;
        }

        Set<String> unique = new LinkedHashSet<>(results);
        if (unique.size() > maxExpandedPatterns) {
            throw new IllegalArgumentException(
                    "expanded pattern limit exceeded: " + unique.size() + " > " + maxExpandedPatterns);
        }
        return new ArrayList<>(unique);
    }

    private static List<List<String>> parseSegments(String pattern) {
        List<List<String>> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < pattern.length();) {
            char current = pattern.charAt(i);
            if (current == '[') {
                int end = pattern.indexOf(']', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("unclosed slot in pattern: " + pattern);
                }
                literal.append(pattern, i, end + 1);
                i = end + 1;
            } else if (current == '(') {
                flushLiteral(segments, literal);
                int end = findGroupEnd(pattern, i);
                List<String> alternatives = splitAlternatives(pattern.substring(i + 1, end), pattern);
                int next = end + 1;
                if (next < pattern.length() && pattern.charAt(next) == '?') {
                    List<String> optional = new ArrayList<>();
                    optional.add("");
                    optional.addAll(alternatives);
                    alternatives = optional;
                    next++;
                }
                segments.add(alternatives);
                i = next;
            } else if (current == ')') {
                throw new IllegalArgumentException("unopened group in pattern: " + pattern);
            } else {
                literal.append(current);
                i++;
            }
        }
        flushLiteral(segments, literal);
        return segments;
    }

    private static void flushLiteral(List<List<String>> segments, StringBuilder literal) {
        if (literal.length() == 0) {
            return;
        }
        List<String> segment = new ArrayList<>();
        segment.add(literal.toString());
        segments.add(segment);
        literal.setLength(0);
    }

    private static int findGroupEnd(String pattern, int start) {
        for (int i = start + 1; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '[') {
                int end = pattern.indexOf(']', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("unclosed slot in pattern: " + pattern);
                }
                i = end;
            } else if (current == '(') {
                throw new IllegalArgumentException("nested group is not supported: " + pattern);
            } else if (current == ')') {
                return i;
            }
        }
        throw new IllegalArgumentException("unclosed group in pattern: " + pattern);
    }

    private static List<String> splitAlternatives(String group, String pattern) {
        List<String> alternatives = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < group.length();) {
            char c = group.charAt(i);
            if (c == '[') {
                int end = group.indexOf(']', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("unclosed slot in pattern: " + pattern);
                }
                current.append(group, i, end + 1);
                i = end + 1;
            } else if (c == '|') {
                addAlternative(alternatives, current, pattern);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        addAlternative(alternatives, current, pattern);
        return alternatives;
    }

    private static void addAlternative(List<String> alternatives, StringBuilder current, String pattern) {
        if (current.length() == 0) {
            throw new IllegalArgumentException("empty alternative is not allowed: " + pattern);
        }
        alternatives.add(current.toString());
        current.setLength(0);
    }
}
