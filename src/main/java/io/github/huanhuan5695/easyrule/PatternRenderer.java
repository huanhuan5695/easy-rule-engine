package io.github.huanhuan5695.easyrule;

final class PatternRenderer {
    private PatternRenderer() {
    }

    static String render(PatternAst.Sequence sequence, PatternSyntax syntax) {
        StringBuilder result = new StringBuilder();
        for (PatternAst.Node node : sequence.nodes()) {
            if (node instanceof PatternAst.Text) {
                appendText(result, (PatternAst.Text) node, syntax);
            } else if (node instanceof PatternAst.Slot) {
                PatternAst.Slot slot = (PatternAst.Slot) node;
                result.append(syntax.slotStart()).append(slot.name()).append(syntax.slotEnd());
            } else {
                throw new IllegalArgumentException("only expanded flat patterns can be rendered");
            }
        }
        return result.toString();
    }

    private static void appendText(StringBuilder result, PatternAst.Text text, PatternSyntax syntax) {
        if (text.escaped()) {
            result.append(syntax.escape()).append(text.value());
            return;
        }
        String value = text.value();
        for (int index = 0; index < value.length();) {
            String token = tokenAt(value, index, syntax);
            if (token != null) {
                result.append(syntax.escape()).append(token);
                index += token.length();
            } else {
                result.append(value.charAt(index));
                index++;
            }
        }
    }

    private static String tokenAt(String value, int index, PatternSyntax syntax) {
        for (String token : syntax.tokensByLength()) {
            if (value.startsWith(token, index)) {
                return token;
            }
        }
        return null;
    }
}
