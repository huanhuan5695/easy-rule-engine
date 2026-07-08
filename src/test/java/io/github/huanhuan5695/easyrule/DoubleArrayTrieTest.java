package io.github.huanhuan5695.easyrule;

import java.util.Arrays;
import java.util.List;

public class DoubleArrayTrieTest {
    public static void main(String[] args) {
        DoubleArrayTrie trie = DoubleArrayTrie.build(Arrays.asList("he", "her", "his", "java"));

        assertEquals(4, trie.size(), "reports indexed word count");
        assertTrue(trie.contains("he"), "contains exact word he");
        assertTrue(trie.contains("her"), "contains exact word her");
        assertTrue(trie.contains("java"), "contains exact word java");
        assertFalse(trie.contains("hero"), "does not treat longer word as match");
        assertFalse(trie.contains("h"), "does not treat prefix as full word");

        assertTrue(trie.startsWith("h"), "detects existing prefix h");
        assertTrue(trie.startsWith("ja"), "detects existing prefix ja");
        assertFalse(trie.startsWith("x"), "rejects missing prefix");

        List<String> prefixes = trie.commonPrefixSearch("herself");
        assertEquals(Arrays.asList("he", "her"), prefixes, "finds words that prefix input");

        assertThrows(UnsupportedOperationException.class, () -> prefixes.clear(), "prefix results are immutable");
        assertThrows(IllegalArgumentException.class, () -> DoubleArrayTrie.build(null), "null word collection rejected");

        DoubleArrayTrie compacted = DoubleArrayTrie.build(Arrays.asList("he", "he", "", null, "her"));
        assertEquals(2, compacted.size(), "counts unique non-empty words");

        System.out.println("All DoubleArrayTrie tests passed.");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getName() + ", got " + actual, actual);
        }
        throw new AssertionError(message + ": expected " + expected.getName());
    }

    private interface ThrowingRunnable {
        void run();
    }
}
