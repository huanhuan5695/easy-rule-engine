package io.github.huanhuan5695.easyrule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable template matcher for exact templates and slot-sequence templates.
 *
 * <p>Build a matcher with {@link #builder()}, add slot dictionaries and
 * {@link RulePattern} instances, then call {@link #match(String)}. Built
 * matchers are snapshots of the builder state and are safe to share across
 * threads after construction.
 */
public final class TemplateMatcher {
    private static final int DEFAULT_MAX_STATES = 10_000;
    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final Comparator<MatchResult> RESULT_ORDER =
            Comparator.comparingInt(MatchResult::priority).reversed()
                    .thenComparing(Comparator.comparingInt(MatchResult::capturedTextLength).reversed())
                    .thenComparing(MatchResult::category)
                    .thenComparing(MatchResult::templateId);

    private final Node root;
    private final List<SequenceTemplate> sequenceTemplates;
    private final Map<String, DoubleArrayTrie> slotDictionaries;
    private final SlotScanIndex slotScanIndex;
    private final int maxStates;
    private final int maxResults;

    private TemplateMatcher(
            Node root,
            List<SequenceTemplate> sequenceTemplates,
            Map<String, DoubleArrayTrie> slotDictionaries,
            SlotScanIndex slotScanIndex,
            int maxStates,
            int maxResults) {
        this.root = root;
        this.sequenceTemplates = sequenceTemplates;
        this.slotDictionaries = slotDictionaries;
        this.slotScanIndex = slotScanIndex;
        this.maxStates = maxStates;
        this.maxResults = maxResults;
    }

    /**
     * Creates a new matcher builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Matches input with default options.
     *
     * <p>The default strategy is {@link MatchMode#EXACT_THEN_SLOT_SEQUENCE}.
     * Returned result lists and nested capture collections are immutable.
     *
     * @param input input text; {@code null} returns an empty result list
     * @return immutable match results
     */
    public List<MatchResult> match(String input) {
        return match(input, MatchOptions.defaultOptions());
    }

    /**
     * Matches input with per-call options.
     *
     * @param input input text; {@code null} returns an empty result list
     * @param options per-call options; {@code null} uses default options
     * @return immutable match results
     */
    public List<MatchResult> match(String input, MatchOptions options) {
        if (input == null) {
            return Collections.emptyList();
        }
        MatchOptions effectiveOptions = options == null ? MatchOptions.defaultOptions() : options;
        int effectiveMaxResults = effectiveOptions.maxResults() == null
                ? maxResults
                : Math.min(maxResults, effectiveOptions.maxResults());
        int effectiveMaxStates = effectiveOptions.maxStates() == null
                ? maxStates
                : Math.min(maxStates, effectiveOptions.maxStates());
        StateBudget stateBudget = new StateBudget(effectiveMaxStates);

        if (effectiveOptions.mode() == MatchMode.EXACT_ONLY) {
            return matchExactTemplates(input, effectiveMaxResults, stateBudget);
        }
        if (effectiveOptions.mode() == MatchMode.SLOT_SEQUENCE_ONLY) {
            return matchSlotSequences(input, effectiveMaxResults, stateBudget);
        }
        if (effectiveOptions.mode() == MatchMode.ALL) {
            List<MatchResult> results = new ArrayList<>();
            results.addAll(matchExactTemplates(input, maxResults, stateBudget));
            results.addAll(matchSlotSequences(input, maxResults, stateBudget));
            return sortAndLimit(results, effectiveMaxResults);
        }

        List<MatchResult> exactResults = matchExactTemplates(input, effectiveMaxResults, stateBudget);
        if (!exactResults.isEmpty()) {
            return exactResults;
        }
        return matchSlotSequences(input, effectiveMaxResults, stateBudget);
    }

    private List<MatchResult> matchExactTemplates(String input, int resultLimit, StateBudget stateBudget) {
        List<MatchResult> results = new ArrayList<>();
        ArrayDeque<MatchState> queue = new ArrayDeque<>();
        queue.add(new MatchState(root, 0, new LinkedHashMap<String, List<SlotCapture>>()));

        while (!queue.isEmpty()) {
            stateBudget.visit("exact template matching");

            MatchState state = queue.removeFirst();
            if (state.inputPos == input.length() && !state.node.outputs.isEmpty()) {
                for (TemplateMeta output : state.node.outputs) {
                    results.add(new MatchResult(
                            output.category,
                            output.templateId,
                            output.priority,
                            PatternMode.EXACT,
                            state.captures));
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

                List<String> candidates = new ArrayList<>(dictionary.commonPrefixSearch(input, state.inputPos));
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

        return sortAndLimit(results, resultLimit);
    }

    private List<MatchResult> matchSlotSequences(String input, int resultLimit, StateBudget stateBudget) {
        List<MatchResult> results = new ArrayList<>();
        Map<String, List<SlotHit>> hitsBySlot = collectSlotHits(input);

        for (SequenceTemplate template : sequenceTemplates) {
            ArrayDeque<SequenceState> queue = new ArrayDeque<>();
            queue.add(new SequenceState(0, 0, new LinkedHashMap<String, List<SlotCapture>>()));

            while (!queue.isEmpty()) {
                stateBudget.visit("slot sequence matching");

                SequenceState state = queue.removeFirst();
                if (state.slotIndex == template.slotNames.size()) {
                    results.add(new MatchResult(
                            template.category,
                            template.templateId,
                            template.priority,
                            PatternMode.SLOT_SEQUENCE,
                            state.captures));
                    continue;
                }

                String slotName = template.slotNames.get(state.slotIndex);
                List<SlotHit> hits = hitsBySlot.get(slotName);
                if (hits == null) {
                    continue;
                }

                for (SlotHit hit : hits) {
                    if (hit.start >= state.searchPos) {
                        queue.addLast(new SequenceState(
                                state.slotIndex + 1,
                                hit.end,
                                appendCapture(state.captures, slotName, hit.value, hit.start, hit.end)));
                    }
                }
            }
        }

        return sortAndLimit(results, resultLimit);
    }

    private Map<String, List<SlotHit>> collectSlotHits(String input) {
        Map<String, List<SlotHit>> hitsBySlot = slotScanIndex.scan(input);
        for (String slotName : requiredSequenceSlotNames()) {
            if (slotScanIndex.indexesSlot(slotName)) {
                continue;
            }
            DoubleArrayTrie dictionary = slotDictionaries.get(slotName);
            if (dictionary == null) {
                continue;
            }

            List<SlotHit> hits = new ArrayList<>();
            for (int pos = 0; pos < input.length(); pos++) {
                List<String> candidates = new ArrayList<>(dictionary.commonPrefixSearch(input, pos));
                candidates.sort(Comparator.comparingInt(String::length).reversed());
                for (String candidate : candidates) {
                    if (!candidate.isEmpty()) {
                        hits.add(new SlotHit(candidate, pos, pos + candidate.length()));
                    }
                }
            }
            if (!hits.isEmpty()) {
                hitsBySlot.put(slotName, hits);
            }
        }
        return hitsBySlot;
    }

    private List<String> requiredSequenceSlotNames() {
        List<String> slotNames = new ArrayList<>();
        for (SequenceTemplate template : sequenceTemplates) {
            for (String slotName : template.slotNames) {
                if (!slotNames.contains(slotName)) {
                    slotNames.add(slotName);
                }
            }
        }
        return slotNames;
    }

    private static List<MatchResult> sortAndLimit(List<MatchResult> results, int maxResults) {
        results.sort(RESULT_ORDER);
        if (results.size() > maxResults) {
            return Collections.unmodifiableList(new ArrayList<>(results.subList(0, maxResults)));
        }
        return Collections.unmodifiableList(new ArrayList<>(results));
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

    /**
     * Mutable builder used to create immutable {@link TemplateMatcher} snapshots.
     */
    public static final class Builder {
        private final Node root = new Node();
        private final List<SequenceTemplate> sequenceTemplates = new ArrayList<>();
        private final Set<String> referencedSlotNames = new TreeSet<>();
        private final Map<String, DoubleArrayTrie> slotDictionaries = new HashMap<>();
        private final Map<String, List<String>> slotValues = new HashMap<>();
        private int maxStates = DEFAULT_MAX_STATES;
        private int maxResults = DEFAULT_MAX_RESULTS;
        private boolean strictSlotValidation;

        private Builder() {
        }

        /**
         * Adds or replaces a slot dictionary from raw values.
         *
         * <p>Values are indexed for exact-template matching and for the shared
         * slot-sequence scan index.
         *
         * @param slotName slot name used in patterns without brackets
         * @param values dictionary values; null and empty entries are ignored
         * @return this builder
         */
        public Builder addSlotDictionary(String slotName, Collection<String> values) {
            validateSlotName(slotName);
            if (values == null) {
                throw new IllegalArgumentException("values is required");
            }
            slotDictionaries.put(slotName, DoubleArrayTrie.build(values));
            slotValues.put(slotName, copyDictionaryValues(values));
            return this;
        }

        /**
         * Adds or replaces a slot dictionary from an existing trie.
         *
         * <p>Tries supplied this way are used for exact matching and fallback
         * slot-sequence scanning, but their raw values are not available to the
         * shared scan index.
         *
         * @param slotName slot name used in patterns without brackets
         * @param trie immutable dictionary trie
         * @return this builder
         */
        public Builder addSlotDictionary(String slotName, DoubleArrayTrie trie) {
            validateSlotName(slotName);
            if (trie == null) {
                throw new IllegalArgumentException("trie is required");
            }
            slotDictionaries.put(slotName, trie);
            slotValues.remove(slotName);
            return this;
        }

        /**
         * Adds a template and infers its mode from the pattern shape.
         *
         * @param category result category
         * @param templateId result template id
         * @param pattern pattern text
         * @return this builder
         */
        public Builder addTemplate(String category, String templateId, String pattern) {
            return addPattern(RulePattern.of(category, templateId, pattern, inferMode(pattern)));
        }

        /**
         * Adds a rule pattern with explicit mode and priority.
         *
         * @param pattern rule pattern
         * @return this builder
         */
        public Builder addPattern(RulePattern pattern) {
            if (pattern == null) {
                throw new IllegalArgumentException("pattern is required");
            }
            List<Token> tokens = parsePattern(pattern.pattern());
            registerReferencedSlots(tokens);
            if (pattern.mode() == PatternMode.SLOT_SEQUENCE) {
                if (!isSlotSequencePattern(tokens)) {
                    throw new IllegalArgumentException(
                            "slot sequence pattern can only contain slots and '_' separators: "
                                    + pattern.pattern());
                }
                sequenceTemplates.add(new SequenceTemplate(
                        pattern.category(),
                        pattern.templateId(),
                        pattern.priority(),
                        slotNames(tokens)));
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
            node.outputs.add(new TemplateMeta(pattern.category(), pattern.templateId(), pattern.priority()));
            return this;
        }

        /**
         * Enables build-time validation that every referenced slot has a dictionary.
         *
         * @return this builder
         */
        public Builder strictSlotValidation() {
            return strictSlotValidation(true);
        }

        /**
         * Enables or disables build-time validation of referenced slot dictionaries.
         *
         * @param enabled whether strict validation is enabled
         * @return this builder
         */
        public Builder strictSlotValidation(boolean enabled) {
            this.strictSlotValidation = enabled;
            return this;
        }

        private static PatternMode inferMode(String pattern) {
            List<Token> tokens = parsePattern(pattern);
            if (isSlotSequencePattern(tokens)) {
                return PatternMode.SLOT_SEQUENCE;
            }
            return PatternMode.EXACT;
        }

        /**
         * Sets the maximum number of internal states visited per match call.
         *
         * @param maxStates positive state limit
         * @return this builder
         */
        public Builder maxStates(int maxStates) {
            if (maxStates <= 0) {
                throw new IllegalArgumentException("maxStates must be positive");
            }
            this.maxStates = maxStates;
            return this;
        }

        /**
         * Sets the matcher-level maximum number of returned results.
         *
         * @param maxResults positive result limit
         * @return this builder
         */
        public Builder maxResults(int maxResults) {
            if (maxResults <= 0) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Builds an immutable matcher snapshot.
         *
         * @return immutable matcher
         */
        public TemplateMatcher build() {
            if (strictSlotValidation) {
                validateReferencedSlotDictionaries();
            }
            return new TemplateMatcher(
                    copyNode(root),
                    new ArrayList<>(sequenceTemplates),
                    new HashMap<>(slotDictionaries),
                    SlotScanIndex.build(requiredSequenceSlotNames(sequenceTemplates), slotValues),
                    maxStates,
                    maxResults);
        }

        private void validateReferencedSlotDictionaries() {
            List<String> missing = new ArrayList<>();
            for (String slotName : referencedSlotNames) {
                if (!slotDictionaries.containsKey(slotName)) {
                    missing.add(slotName);
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("missing slot dictionaries: " + String.join(", ", missing));
            }
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

        private static List<String> requiredSequenceSlotNames(List<SequenceTemplate> templates) {
            List<String> slotNames = new ArrayList<>();
            for (SequenceTemplate template : templates) {
                for (String slotName : template.slotNames) {
                    if (!slotNames.contains(slotName)) {
                        slotNames.add(slotName);
                    }
                }
            }
            return slotNames;
        }

        private static List<String> copyDictionaryValues(Collection<String> values) {
            List<String> copy = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String value : values) {
                if (value != null && !value.isEmpty() && seen.add(value)) {
                    copy.add(value);
                }
            }
            return copy;
        }

        private void registerReferencedSlots(List<Token> tokens) {
            for (Token token : tokens) {
                if (token.slotName != null) {
                    referencedSlotNames.add(token.slotName);
                }
            }
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

        private static Node copyNode(Node source) {
            Node copy = new Node();
            copy.outputs.addAll(source.outputs);
            for (Map.Entry<Character, Node> entry : source.charChildren.entrySet()) {
                copy.charChildren.put(entry.getKey(), copyNode(entry.getValue()));
            }
            for (SlotEdge edge : source.slotEdges) {
                copy.slotEdges.add(new SlotEdge(edge.slotName, copyNode(edge.next)));
            }
            return copy;
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

    /**
     * Immutable match result containing routing metadata and captured slots.
     */
    public static final class MatchResult {
        private final String category;
        private final String templateId;
        private final int priority;
        private final PatternMode mode;
        private final Map<String, List<SlotCapture>> slotCaptures;
        private final Map<String, List<String>> captures;

        private MatchResult(
                String category,
                String templateId,
                int priority,
                PatternMode mode,
                Map<String, List<SlotCapture>> slotCaptures) {
            this.category = category;
            this.templateId = templateId;
            this.priority = priority;
            this.mode = mode;
            this.slotCaptures = freezeSlotCaptures(slotCaptures);
            this.captures = freezeValues(this.slotCaptures);
        }

        /**
         * Returns the matched rule category.
         *
         * @return category
         */
        public String category() {
            return category;
        }

        /**
         * Returns the matched rule template id.
         *
         * @return template id
         */
        public String templateId() {
            return templateId;
        }

        /**
         * Returns the mode of the pattern that matched.
         *
         * @return pattern mode
         */
        public PatternMode mode() {
            return mode;
        }

        /**
         * Returns the matched rule priority.
         *
         * @return priority
         */
        public int priority() {
            return priority;
        }

        /**
         * Returns captured slot values without positions.
         *
         * @return immutable map from slot name to immutable captured values
         */
        public Map<String, List<String>> captures() {
            return captures;
        }

        /**
         * Returns captured slot values with source positions.
         *
         * @return immutable map from slot name to immutable captures
         */
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

        private int capturedTextLength() {
            int length = 0;
            for (List<SlotCapture> captures : slotCaptures.values()) {
                for (SlotCapture capture : captures) {
                    length += capture.end() - capture.start();
                }
            }
            return length;
        }
    }

    /**
     * Immutable captured slot value and its source span.
     *
     * <p>{@code start} is inclusive and {@code end} is exclusive, matching
     * {@link String#substring(int, int)} semantics.
     */
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

        /**
         * Returns the slot name.
         *
         * @return slot name
         */
        public String slotName() {
            return slotName;
        }

        /**
         * Returns the captured value.
         *
         * @return captured value
         */
        public String value() {
            return value;
        }

        /**
         * Returns the inclusive start offset in the input string.
         *
         * @return start offset
         */
        public int start() {
            return start;
        }

        /**
         * Returns the exclusive end offset in the input string.
         *
         * @return end offset
         */
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
        private final int priority;

        private TemplateMeta(String category, String templateId, int priority) {
            this.category = category;
            this.templateId = templateId;
            this.priority = priority;
        }
    }

    private static final class SequenceTemplate {
        private final String category;
        private final String templateId;
        private final int priority;
        private final List<String> slotNames;

        private SequenceTemplate(String category, String templateId, int priority, List<String> slotNames) {
            this.category = category;
            this.templateId = templateId;
            this.priority = priority;
            this.slotNames = slotNames;
        }
    }

    private static final class SlotHit {
        private final String value;
        private final int start;
        private final int end;

        private SlotHit(String value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    private static final class SlotScanIndex {
        private final ScanNode root;
        private final Set<String> indexedSlotNames;

        private SlotScanIndex(ScanNode root, Set<String> indexedSlotNames) {
            this.root = root;
            this.indexedSlotNames = indexedSlotNames;
        }

        private static SlotScanIndex build(
                List<String> requiredSlotNames,
                Map<String, List<String>> slotValues) {
            ScanNode root = new ScanNode();
            Set<String> indexedSlotNames = new HashSet<>();
            for (String slotName : requiredSlotNames) {
                List<String> values = slotValues.get(slotName);
                if (values == null || values.isEmpty()) {
                    continue;
                }
                indexedSlotNames.add(slotName);
                for (String value : values) {
                    addValue(root, slotName, value);
                }
            }
            buildFailureLinks(root);
            return new SlotScanIndex(root, indexedSlotNames);
        }

        private boolean indexesSlot(String slotName) {
            return indexedSlotNames.contains(slotName);
        }

        private Map<String, List<SlotHit>> scan(String input) {
            Map<String, List<SlotHit>> hitsBySlot = new HashMap<>();
            ScanNode node = root;
            for (int i = 0; i < input.length(); i++) {
                char current = input.charAt(i);
                while (node != root && !node.children.containsKey(current)) {
                    node = node.failure;
                }
                ScanNode next = node.children.get(current);
                if (next != null) {
                    node = next;
                }
                for (SlotOutput output : node.outputs) {
                    int start = i + 1 - output.value.length();
                    hitsBySlot.computeIfAbsent(output.slotName, ignored -> new ArrayList<>())
                            .add(new SlotHit(output.value, start, i + 1));
                }
            }
            return hitsBySlot;
        }

        private static void addValue(ScanNode root, String slotName, String value) {
            ScanNode node = root;
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                node = node.children.computeIfAbsent(current, ignored -> new ScanNode());
            }
            node.outputs.add(new SlotOutput(slotName, value));
        }

        private static void buildFailureLinks(ScanNode root) {
            ArrayDeque<ScanNode> queue = new ArrayDeque<>();
            root.failure = root;
            for (ScanNode child : root.children.values()) {
                child.failure = root;
                queue.addLast(child);
            }

            while (!queue.isEmpty()) {
                ScanNode node = queue.removeFirst();
                for (Map.Entry<Character, ScanNode> entry : node.children.entrySet()) {
                    char transition = entry.getKey();
                    ScanNode child = entry.getValue();
                    ScanNode failure = node.failure;
                    while (failure != root && !failure.children.containsKey(transition)) {
                        failure = failure.failure;
                    }
                    ScanNode fallback = failure.children.get(transition);
                    child.failure = fallback == null ? root : fallback;
                    child.outputs.addAll(child.failure.outputs);
                    queue.addLast(child);
                }
            }
        }
    }

    private static final class ScanNode {
        private final Map<Character, ScanNode> children = new HashMap<>();
        private final List<SlotOutput> outputs = new ArrayList<>();
        private ScanNode failure;
    }

    private static final class SlotOutput {
        private final String slotName;
        private final String value;

        private SlotOutput(String slotName, String value) {
            this.slotName = slotName;
            this.value = value;
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

    private static final class StateBudget {
        private final int limit;
        private int visited;

        private StateBudget(int limit) {
            this.limit = limit;
        }

        private void visit(String phase) {
            if (++visited > limit) {
                throw new IllegalStateException(phase + " exceeded maxStates=" + limit);
            }
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
