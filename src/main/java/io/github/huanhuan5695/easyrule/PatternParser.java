package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.List;

final class PatternParser {
    private final String pattern;
    private final PatternSyntax syntax;
    private final boolean expanded;
    private int index;

    private PatternParser(String pattern, PatternSyntax syntax, boolean expanded) {
        this.pattern = pattern;
        this.syntax = syntax;
        this.expanded = expanded;
    }

    static PatternAst.Sequence parse(String pattern, PatternSyntax syntax) {
        return parse(pattern, syntax, false);
    }

    static PatternAst.Sequence parseExpanded(String pattern, PatternSyntax syntax) {
        return parse(pattern, syntax, true);
    }

    private static PatternAst.Sequence parse(String pattern, PatternSyntax syntax, boolean expanded) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern is required");
        }
        if (syntax == null) {
            throw new IllegalArgumentException("patternSyntax is required");
        }
        PatternParser parser = new PatternParser(pattern, syntax, expanded);
        PatternAst.Sequence result = parser.parseSequence(false);
        if (parser.index != pattern.length()) {
            throw new IllegalArgumentException("unexpected pattern token at index " + parser.index + ": " + pattern);
        }
        return result;
    }

    private PatternAst.Sequence parseSequence(boolean inGroup) {
        List<PatternAst.Node> nodes = new ArrayList<>();
        StringBuilder literal = new StringBuilder();

        while (index < pattern.length()) {
            if (startsWith(syntax.escape())) {
                flushLiteral(nodes, literal);
                nodes.add(new PatternAst.Text(readEscapedLiteral(), true));
                continue;
            }
            if (startsWith(syntax.slotStart())) {
                flushLiteral(nodes, literal);
                nodes.add(readSlot());
                continue;
            }
            if (expanded && inGroup && startsWith(syntax.groupEnd())) {
                break;
            }
            if (startsWith(syntax.slotEnd())) {
                throw new IllegalArgumentException("unopened slot in pattern: " + pattern);
            }
            if (expanded && startsWith(syntax.groupStart())) {
                if (inGroup) {
                    throw new IllegalArgumentException("nested group is not supported: " + pattern);
                }
                flushLiteral(nodes, literal);
                PatternAst.Node group = readGroup();
                if (startsWith(syntax.optional())) {
                    index += syntax.optional().length();
                    group = new PatternAst.OptionalNode(group);
                }
                nodes.add(group);
                continue;
            }
            if (expanded && startsWith(syntax.groupEnd())) {
                if (inGroup) {
                    break;
                }
                throw new IllegalArgumentException("unopened group in pattern: " + pattern);
            }
            if (expanded && inGroup && startsWith(syntax.alternative())) {
                break;
            }

            literal.append(pattern.charAt(index));
            index++;
        }

        flushLiteral(nodes, literal);
        return new PatternAst.Sequence(nodes);
    }

    private PatternAst.Slot readSlot() {
        index += syntax.slotStart().length();
        int end = pattern.indexOf(syntax.slotEnd(), index);
        if (end < 0) {
            throw new IllegalArgumentException("unclosed slot in pattern: " + pattern);
        }
        String slotName = pattern.substring(index, end);
        validateSlotName(slotName);
        index = end + syntax.slotEnd().length();
        return new PatternAst.Slot(slotName);
    }

    private PatternAst.Alternatives readGroup() {
        index += syntax.groupStart().length();
        List<PatternAst.Sequence> alternatives = new ArrayList<>();
        while (true) {
            if (index >= pattern.length()) {
                throw new IllegalArgumentException("unclosed group in pattern: " + pattern);
            }
            if (startsWith(syntax.groupEnd()) || startsWith(syntax.alternative())) {
                throw new IllegalArgumentException("empty alternative is not allowed: " + pattern);
            }

            alternatives.add(parseSequence(true));
            if (startsWith(syntax.alternative())) {
                index += syntax.alternative().length();
                continue;
            }
            if (startsWith(syntax.groupEnd())) {
                index += syntax.groupEnd().length();
                return new PatternAst.Alternatives(alternatives);
            }
            throw new IllegalArgumentException("unclosed group in pattern: " + pattern);
        }
    }

    private String readEscapedLiteral() {
        index += syntax.escape().length();
        if (index >= pattern.length()) {
            throw new IllegalArgumentException("dangling escape in pattern: " + pattern);
        }
        for (String token : syntax.tokensByLength()) {
            if (pattern.startsWith(token, index)) {
                index += token.length();
                return token;
            }
        }
        char escaped = pattern.charAt(index);
        index++;
        return String.valueOf(escaped);
    }

    private boolean startsWith(String token) {
        return pattern.startsWith(token, index);
    }

    private static void flushLiteral(List<PatternAst.Node> nodes, StringBuilder literal) {
        if (literal.length() > 0) {
            nodes.add(new PatternAst.Text(literal.toString(), false));
            literal.setLength(0);
        }
    }

    static void validateSlotName(String slotName) {
        if (slotName == null || slotName.isEmpty()) {
            throw new IllegalArgumentException("slotName is required");
        }
        for (int i = 0; i < slotName.length(); i++) {
            char c = slotName.charAt(i);
            boolean valid = Character.isLetterOrDigit(c) || c == '_' || c == '-';
            if (!valid) {
                throw new IllegalArgumentException("invalid slotName: " + slotName);
            }
        }
    }
}
