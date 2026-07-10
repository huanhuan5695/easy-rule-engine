package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class PatternAst {
    private PatternAst() {
    }

    interface Node {
    }

    static final class Sequence implements Node {
        private final List<Node> nodes;

        Sequence(List<Node> nodes) {
            List<Node> normalized = new ArrayList<>();
            for (Node node : nodes) {
                if (node instanceof Text && !normalized.isEmpty()) {
                    Node previous = normalized.get(normalized.size() - 1);
                    Text currentText = (Text) node;
                    if (previous instanceof Text && ((Text) previous).escaped == currentText.escaped) {
                        Text previousText = (Text) previous;
                        normalized.set(
                                normalized.size() - 1,
                                new Text(previousText.value + currentText.value, currentText.escaped));
                        continue;
                    }
                }
                normalized.add(node);
            }
            this.nodes = Collections.unmodifiableList(normalized);
        }

        List<Node> nodes() {
            return nodes;
        }

        Sequence append(Sequence other) {
            List<Node> combined = new ArrayList<>(nodes.size() + other.nodes.size());
            combined.addAll(nodes);
            combined.addAll(other.nodes);
            return new Sequence(combined);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Sequence && nodes.equals(((Sequence) other).nodes);
        }

        @Override
        public int hashCode() {
            return nodes.hashCode();
        }
    }

    static final class Text implements Node {
        private final String value;
        private final boolean escaped;

        Text(String value, boolean escaped) {
            this.value = value;
            this.escaped = escaped;
        }

        String value() {
            return value;
        }

        boolean escaped() {
            return escaped;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Text)) {
                return false;
            }
            Text that = (Text) other;
            return escaped == that.escaped && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, escaped);
        }
    }

    static final class Slot implements Node {
        private final String name;

        Slot(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Slot && name.equals(((Slot) other).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    static final class Alternatives implements Node {
        private final List<Sequence> alternatives;

        Alternatives(List<Sequence> alternatives) {
            this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
        }

        List<Sequence> alternatives() {
            return alternatives;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Alternatives
                    && alternatives.equals(((Alternatives) other).alternatives);
        }

        @Override
        public int hashCode() {
            return alternatives.hashCode();
        }
    }

    static final class OptionalNode implements Node {
        private final Node node;

        OptionalNode(Node node) {
            this.node = node;
        }

        Node node() {
            return node;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OptionalNode && node.equals(((OptionalNode) other).node);
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }
    }
}
