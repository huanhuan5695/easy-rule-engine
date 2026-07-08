package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable double-array trie for exact word lookup and common-prefix search.
 *
 * <p>The trie is built from a static word collection. Null and empty words are
 * ignored during construction. Instances are safe to share across threads after
 * construction because they expose no mutating operations.
 */
public final class DoubleArrayTrie {
    private static final int ROOT = 1;
    private static final int TERMINAL = 0;

    private final int[] base;
    private final int[] check;
    private final String[] values;
    private final Map<Character, Integer> codes;

    private DoubleArrayTrie(int[] base, int[] check, String[] values, Map<Character, Integer> codes) {
        this.base = base;
        this.check = check;
        this.values = values;
        this.codes = codes;
    }

    /**
     * Builds a trie from the supplied words.
     *
     * @param words words to index; null and empty entries are ignored
     * @return immutable trie
     */
    public static DoubleArrayTrie build(Collection<String> words) {
        BuildNode root = new BuildNode();
        Map<Character, Integer> codes = buildCharacterCodes(words);

        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            BuildNode node = root;
            for (int i = 0; i < word.length(); i++) {
                int code = codes.get(word.charAt(i));
                node = node.children.computeIfAbsent(code, ignored -> new BuildNode());
            }
            node.children.computeIfAbsent(TERMINAL, ignored -> new BuildNode()).value = word;
        }

        Builder builder = new Builder(codes);
        builder.check[ROOT] = -1;
        builder.place(root, ROOT);
        return builder.toTrie();
    }

    /**
     * Returns whether the trie contains the exact word.
     *
     * @param word candidate word
     * @return {@code true} if the exact word exists
     */
    public boolean contains(String word) {
        int node = traverse(word);
        if (node < 0) {
            return false;
        }
        int terminal = base[node] + TERMINAL;
        return terminal < check.length && check[terminal] == node && values[terminal] != null;
    }

    /**
     * Returns whether the trie contains the prefix path.
     *
     * @param prefix candidate prefix
     * @return {@code true} if the prefix path exists
     */
    public boolean startsWith(String prefix) {
        return traverse(prefix) >= 0;
    }

    /**
     * Finds dictionary words that are prefixes of {@code text}.
     *
     * @param text text to scan from offset {@code 0}
     * @return matching dictionary words in increasing end-position order
     */
    public List<String> commonPrefixSearch(String text) {
        return commonPrefixSearch(text, 0);
    }

    /**
     * Finds dictionary words that are prefixes of {@code text.substring(offset)}.
     *
     * @param text text to scan
     * @param offset zero-based scan offset
     * @return matching dictionary words in increasing end-position order
     */
    public List<String> commonPrefixSearch(String text, int offset) {
        if (text == null) {
            return Collections.emptyList();
        }
        if (offset < 0 || offset > text.length()) {
            throw new IllegalArgumentException("offset out of range: " + offset);
        }

        List<String> matches = new ArrayList<>();
        int node = ROOT;
        for (int i = offset; i < text.length(); i++) {
            Integer code = codes.get(text.charAt(i));
            if (code == null) {
                break;
            }

            int next = transition(node, code);
            if (next < 0) {
                break;
            }
            node = next;

            int terminal = transition(node, TERMINAL);
            if (terminal >= 0 && values[terminal] != null) {
                matches.add(values[terminal]);
            }
        }
        return matches;
    }

    private int traverse(String text) {
        if (text == null) {
            return -1;
        }

        int node = ROOT;
        for (int i = 0; i < text.length(); i++) {
            Integer code = codes.get(text.charAt(i));
            if (code == null) {
                return -1;
            }

            node = transition(node, code);
            if (node < 0) {
                return -1;
            }
        }
        return node;
    }

    private int transition(int node, int code) {
        if (node <= 0 || node >= base.length) {
            return -1;
        }

        int next = base[node] + code;
        if (next <= 0 || next >= check.length || check[next] != node) {
            return -1;
        }
        return next;
    }

    private static Map<Character, Integer> buildCharacterCodes(Collection<String> words) {
        Map<Character, Integer> codes = new TreeMap<>();
        for (String word : words) {
            if (word == null) {
                continue;
            }
            for (int i = 0; i < word.length(); i++) {
                codes.putIfAbsent(word.charAt(i), codes.size() + 1);
            }
        }
        return new HashMap<>(codes);
    }

    private static final class BuildNode {
        private final Map<Integer, BuildNode> children = new TreeMap<>();
        private String value;
    }

    private static final class Builder {
        private int[] base = new int[64];
        private int[] check = new int[64];
        private String[] values = new String[64];
        private final Map<Character, Integer> codes;

        private Builder(Map<Character, Integer> codes) {
            this.codes = codes;
        }

        private void place(BuildNode node, int index) {
            if (node.value != null) {
                values[index] = node.value;
            }
            if (node.children.isEmpty()) {
                return;
            }

            int offset = findOffset(node.children.keySet());
            base[index] = offset;

            for (Map.Entry<Integer, BuildNode> entry : node.children.entrySet()) {
                int childIndex = offset + entry.getKey();
                ensureCapacity(childIndex);
                check[childIndex] = index;
            }

            for (Map.Entry<Integer, BuildNode> entry : node.children.entrySet()) {
                place(entry.getValue(), offset + entry.getKey());
            }
        }

        private int findOffset(Collection<Integer> childCodes) {
            int offset = 1;
            while (true) {
                boolean available = true;
                for (int code : childCodes) {
                    int target = offset + code;
                    ensureCapacity(target);
                    if (target == ROOT || check[target] != 0) {
                        available = false;
                        break;
                    }
                }
                if (available) {
                    return offset;
                }
                offset++;
            }
        }

        private void ensureCapacity(int index) {
            if (index < base.length) {
                return;
            }

            int newLength = base.length;
            while (index >= newLength) {
                newLength *= 2;
            }

            int[] newBase = new int[newLength];
            int[] newCheck = new int[newLength];
            String[] newValues = new String[newLength];
            System.arraycopy(base, 0, newBase, 0, base.length);
            System.arraycopy(check, 0, newCheck, 0, check.length);
            System.arraycopy(values, 0, newValues, 0, values.length);
            base = newBase;
            check = newCheck;
            values = newValues;
        }

        private DoubleArrayTrie toTrie() {
            return new DoubleArrayTrie(base, check, values, codes);
        }
    }
}
