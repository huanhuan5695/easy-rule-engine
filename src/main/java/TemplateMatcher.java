import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TemplateMatcher {
    private static final int DEFAULT_MAX_STATES = 10_000;
    private static final int DEFAULT_MAX_RESULTS = 100;

    private final Node root;
    private final List<SequenceTemplate> sequenceTemplates;
    private final Map<String, DoubleArrayTrie> slotDictionaries;
    private final int maxStates;
    private final int maxResults;

    private TemplateMatcher(
            Node root,
            List<SequenceTemplate> sequenceTemplates,
            Map<String, DoubleArrayTrie> slotDictionaries,
            int maxStates,
            int maxResults) {
        this.root = root;
        this.sequenceTemplates = sequenceTemplates;
        this.slotDictionaries = slotDictionaries;
        this.maxStates = maxStates;
        this.maxResults = maxResults;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<MatchResult> match(String input) {
        if (input == null) {
            return Collections.emptyList();
        }

        List<MatchResult> exactResults = matchExactTemplates(input);
        if (!exactResults.isEmpty()) {
            return exactResults;
        }
        return matchSlotSequences(input);
    }

    private List<MatchResult> matchExactTemplates(String input) {
        List<MatchResult> results = new ArrayList<>();
        ArrayDeque<MatchState> queue = new ArrayDeque<>();
        queue.add(new MatchState(root, 0, new LinkedHashMap<String, List<SlotCapture>>()));

        int visitedStates = 0;
        while (!queue.isEmpty()) {
            if (++visitedStates > maxStates) {
                throw new IllegalStateException("exact template matching exceeded maxStates=" + maxStates);
            }

            MatchState state = queue.removeFirst();
            if (state.inputPos == input.length() && !state.node.outputs.isEmpty()) {
                for (TemplateMeta output : state.node.outputs) {
                    results.add(new MatchResult(output.category, output.templateId, state.captures));
                    if (results.size() >= maxResults) {
                        return results;
                    }
                }
            }

            if (state.inputPos < input.length()) {
                Node next = state.node.charChildren.get(input.charAt(state.inputPos));
                if (next != null) {
                    queue.addLast(new MatchState(next, state.inputPos + 1, state.captures));
                }
            }

            for (SlotEdge edge : state.node.slotEdges) {
                DoubleArrayTrie dictionary = slotDictionaries.get(edge.slotName);
                if (dictionary == null) {
                    continue;
                }

                List<String> candidates = dictionary.commonPrefixSearch(input, state.inputPos);
                candidates.sort(Comparator.comparingInt(String::length).reversed());

                for (String candidate : candidates) {
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    int end = state.inputPos + candidate.length();
                    queue.addLast(new MatchState(
                            edge.next,
                            end,
                            appendCapture(state.captures, edge.slotName, candidate, state.inputPos, end)));
                }
            }
        }

        return results;
    }

    private List<MatchResult> matchSlotSequences(String input) {
        List<MatchResult> results = new ArrayList<>();
        int visitedStates = 0;

        for (SequenceTemplate template : sequenceTemplates) {
            ArrayDeque<SequenceState> queue = new ArrayDeque<>();
            queue.add(new SequenceState(0, 0, new LinkedHashMap<String, List<SlotCapture>>()));

            while (!queue.isEmpty()) {
                if (++visitedStates > maxStates) {
                    throw new IllegalStateException("slot sequence matching exceeded maxStates=" + maxStates);
                }

                SequenceState state = queue.removeFirst();
                if (state.slotIndex == template.slotNames.size()) {
                    results.add(new MatchResult(template.category, template.templateId, state.captures));
                    if (results.size() >= maxResults) {
                        return results;
                    }
                    continue;
                }

                String slotName = template.slotNames.get(state.slotIndex);
                DoubleArrayTrie dictionary = slotDictionaries.get(slotName);
                if (dictionary == null) {
                    continue;
                }

                for (int pos = state.searchPos; pos < input.length(); pos++) {
                    List<String> candidates = dictionary.commonPrefixSearch(input, pos);
                    candidates.sort(Comparator.comparingInt(String::length).reversed());

                    for (String candidate : candidates) {
                        if (candidate.isEmpty()) {
                            continue;
                        }
                        int end = pos + candidate.length();
                        queue.addLast(new SequenceState(
                                state.slotIndex + 1,
                                end,
                                appendCapture(state.captures, slotName, candidate, pos, end)));
                    }
                }
            }
        }

        return results;
    }

    private static Map<String, List<SlotCapture>> appendCapture(
            Map<String, List<SlotCapture>> captures,
            String slotName,
            String value,
            int start,
            int end) {
        Map<String, List<SlotCapture>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<SlotCapture>> entry : captures.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        copy.computeIfAbsent(slotName, ignored -> new ArrayList<>())
                .add(new SlotCapture(slotName, value, start, end));
        return copy;
    }

    public static final class Builder {
        private final Node root = new Node();
        private final List<SequenceTemplate> sequenceTemplates = new ArrayList<>();
        private final Map<String, DoubleArrayTrie> slotDictionaries = new HashMap<>();
        private int maxStates = DEFAULT_MAX_STATES;
        private int maxResults = DEFAULT_MAX_RESULTS;

        public Builder addSlotDictionary(String slotName, Collection<String> values) {
            validateSlotName(slotName);
            slotDictionaries.put(slotName, DoubleArrayTrie.build(values));
            return this;
        }

        public Builder addSlotDictionary(String slotName, DoubleArrayTrie trie) {
            validateSlotName(slotName);
            if (trie == null) {
                throw new IllegalArgumentException("trie is required");
            }
            slotDictionaries.put(slotName, trie);
            return this;
        }

        public Builder addTemplate(String category, String templateId, String pattern) {
            if (category == null || category.isEmpty()) {
                throw new IllegalArgumentException("category is required");
            }
            if (templateId == null || templateId.isEmpty()) {
                throw new IllegalArgumentException("templateId is required");
            }
            if (pattern == null || pattern.isEmpty()) {
                throw new IllegalArgumentException("pattern is required");
            }

            List<Token> tokens = parsePattern(pattern);
            if (isSlotSequencePattern(tokens)) {
                sequenceTemplates.add(new SequenceTemplate(category, templateId, slotNames(tokens)));
                return this;
            }

            Node node = root;
            for (Token token : tokens) {
                if (token.slotName == null) {
                    node = node.charChildren.computeIfAbsent(token.character, ignored -> new Node());
                } else {
                    node = findOrCreateSlotEdge(node, token.slotName).next;
                }
            }
            node.outputs.add(new TemplateMeta(category, templateId));
            return this;
        }

        public Builder maxStates(int maxStates) {
            if (maxStates <= 0) {
                throw new IllegalArgumentException("maxStates must be positive");
            }
            this.maxStates = maxStates;
            return this;
        }

        public Builder maxResults(int maxResults) {
            if (maxResults <= 0) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
            this.maxResults = maxResults;
            return this;
        }

        public TemplateMatcher build() {
            return new TemplateMatcher(
                    root,
                    new ArrayList<>(sequenceTemplates),
                    new HashMap<>(slotDictionaries),
                    maxStates,
                    maxResults);
        }

        private static List<Token> parsePattern(String pattern) {
            List<Token> tokens = new ArrayList<>();
            for (int i = 0; i < pattern.length();) {
                char current = pattern.charAt(i);
                if (current == '[') {
                    int end = pattern.indexOf(']', i + 1);
                    if (end < 0) {
                        throw new IllegalArgumentException("unclosed slot in pattern: " + pattern);
                    }
                    String slotName = pattern.substring(i + 1, end);
                    validateSlotName(slotName);
                    tokens.add(Token.slot(slotName));
                    i = end + 1;
                } else {
                    tokens.add(Token.character(current));
                    i++;
                }
            }
            return tokens;
        }

        private static boolean isSlotSequencePattern(List<Token> tokens) {
            boolean hasSlot = false;
            for (Token token : tokens) {
                if (token.slotName != null) {
                    hasSlot = true;
                } else if (token.character != '_') {
                    return false;
                }
            }
            return hasSlot;
        }

        private static List<String> slotNames(List<Token> tokens) {
            List<String> slotNames = new ArrayList<>();
            for (Token token : tokens) {
                if (token.slotName != null) {
                    slotNames.add(token.slotName);
                }
            }
            return slotNames;
        }

        private static SlotEdge findOrCreateSlotEdge(Node node, String slotName) {
            for (SlotEdge edge : node.slotEdges) {
                if (edge.slotName.equals(slotName)) {
                    return edge;
                }
            }

            SlotEdge edge = new SlotEdge(slotName, new Node());
            node.slotEdges.add(edge);
            return edge;
        }

        private static void validateSlotName(String slotName) {
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

    public static final class MatchResult {
        private final String category;
        private final String templateId;
        private final Map<String, List<SlotCapture>> slotCaptures;
        private final Map<String, List<String>> captures;

        private MatchResult(String category, String templateId, Map<String, List<SlotCapture>> slotCaptures) {
            this.category = category;
            this.templateId = templateId;
            this.slotCaptures = freezeSlotCaptures(slotCaptures);
            this.captures = freezeValues(this.slotCaptures);
        }

        public String category() {
            return category;
        }

        public String templateId() {
            return templateId;
        }

        public Map<String, List<String>> captures() {
            return captures;
        }

        public Map<String, List<SlotCapture>> slotCaptures() {
            return slotCaptures;
        }

        private static Map<String, List<SlotCapture>> freezeSlotCaptures(
                Map<String, List<SlotCapture>> captures) {
            Map<String, List<SlotCapture>> frozen = new LinkedHashMap<>();
            for (Map.Entry<String, List<SlotCapture>> entry : captures.entrySet()) {
                frozen.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            return Collections.unmodifiableMap(frozen);
        }

        private static Map<String, List<String>> freezeValues(Map<String, List<SlotCapture>> slotCaptures) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (Map.Entry<String, List<SlotCapture>> entry : slotCaptures.entrySet()) {
                List<String> slotValues = new ArrayList<>();
                for (SlotCapture capture : entry.getValue()) {
                    slotValues.add(capture.value());
                }
                values.put(entry.getKey(), Collections.unmodifiableList(slotValues));
            }
            return Collections.unmodifiableMap(values);
        }
    }

    public static final class SlotCapture {
        private final String slotName;
        private final String value;
        private final int start;
        private final int end;

        private SlotCapture(String slotName, String value, int start, int end) {
            this.slotName = slotName;
            this.value = value;
            this.start = start;
            this.end = end;
        }

        public String slotName() {
            return slotName;
        }

        public String value() {
            return value;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }
    }

    private static final class Node {
        private final Map<Character, Node> charChildren = new HashMap<>();
        private final List<SlotEdge> slotEdges = new ArrayList<>();
        private final List<TemplateMeta> outputs = new ArrayList<>();
    }

    private static final class TemplateMeta {
        private final String category;
        private final String templateId;

        private TemplateMeta(String category, String templateId) {
            this.category = category;
            this.templateId = templateId;
        }
    }

    private static final class SequenceTemplate {
        private final String category;
        private final String templateId;
        private final List<String> slotNames;

        private SequenceTemplate(String category, String templateId, List<String> slotNames) {
            this.category = category;
            this.templateId = templateId;
            this.slotNames = slotNames;
        }
    }

    private static final class SlotEdge {
        private final String slotName;
        private final Node next;

        private SlotEdge(String slotName, Node next) {
            this.slotName = slotName;
            this.next = next;
        }
    }

    private static final class MatchState {
        private final Node node;
        private final int inputPos;
        private final Map<String, List<SlotCapture>> captures;

        private MatchState(Node node, int inputPos, Map<String, List<SlotCapture>> captures) {
            this.node = node;
            this.inputPos = inputPos;
            this.captures = captures;
        }
    }

    private static final class SequenceState {
        private final int slotIndex;
        private final int searchPos;
        private final Map<String, List<SlotCapture>> captures;

        private SequenceState(int slotIndex, int searchPos, Map<String, List<SlotCapture>> captures) {
            this.slotIndex = slotIndex;
            this.searchPos = searchPos;
            this.captures = captures;
        }
    }

    private static final class Token {
        private final Character character;
        private final String slotName;

        private Token(Character character, String slotName) {
            this.character = character;
            this.slotName = slotName;
        }

        private static Token character(char character) {
            return new Token(character, null);
        }

        private static Token slot(String slotName) {
            return new Token(null, slotName);
        }
    }
}
