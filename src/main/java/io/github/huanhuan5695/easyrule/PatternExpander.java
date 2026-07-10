package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PatternExpander {
    private PatternExpander() {
    }

    static List<PatternAst.Sequence> expand(
            PatternAst.Sequence pattern,
            int maxExpandedPatterns) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern is required");
        }
        if (maxExpandedPatterns <= 0) {
            throw new IllegalArgumentException("maxExpandedPatterns must be positive");
        }

        List<PatternAst.Sequence> results = new ArrayList<>();
        results.add(new PatternAst.Sequence(Collections.emptyList()));
        for (PatternAst.Node node : pattern.nodes()) {
            List<PatternAst.Sequence> alternatives = expandNode(node, maxExpandedPatterns);
            Set<PatternAst.Sequence> next = new LinkedHashSet<>();
            for (PatternAst.Sequence prefix : results) {
                for (PatternAst.Sequence alternative : alternatives) {
                    if (next.add(prefix.append(alternative)) && next.size() > maxExpandedPatterns) {
                        throw limitExceeded(next.size(), maxExpandedPatterns);
                    }
                }
            }
            results = new ArrayList<>(next);
        }
        return results;
    }

    private static List<PatternAst.Sequence> expandNode(
            PatternAst.Node node,
            int maxExpandedPatterns) {
        if (node instanceof PatternAst.Alternatives) {
            Set<PatternAst.Sequence> alternatives = new LinkedHashSet<>();
            for (PatternAst.Sequence alternative : ((PatternAst.Alternatives) node).alternatives()) {
                addAllBounded(
                        alternatives,
                        expand(alternative, maxExpandedPatterns),
                        maxExpandedPatterns);
            }
            return new ArrayList<>(alternatives);
        }
        if (node instanceof PatternAst.OptionalNode) {
            Set<PatternAst.Sequence> alternatives = new LinkedHashSet<>();
            alternatives.add(new PatternAst.Sequence(Collections.emptyList()));
            PatternAst.Node optionalNode = ((PatternAst.OptionalNode) node).node();
            addAllBounded(
                    alternatives,
                    expandNode(optionalNode, maxExpandedPatterns),
                    maxExpandedPatterns);
            return new ArrayList<>(alternatives);
        }
        return Collections.singletonList(
                new PatternAst.Sequence(Collections.singletonList(node)));
    }

    private static void addAllBounded(
            Set<PatternAst.Sequence> target,
            List<PatternAst.Sequence> values,
            int limit) {
        for (PatternAst.Sequence value : values) {
            if (target.add(value) && target.size() > limit) {
                throw limitExceeded(target.size(), limit);
            }
        }
    }

    private static IllegalArgumentException limitExceeded(int observed, int limit) {
        return new IllegalArgumentException(
                "expanded pattern limit exceeded: " + observed + " > " + limit);
    }
}
