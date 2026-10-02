package jsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader, so the project stays dependency-free. Objects become
 * {@code Map<String,Object>}, arrays {@code List<Object>}, numbers
 * {@code Double}, and {@code null} stays {@code null}.
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException if {@code text} is not valid JSON */
    static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipSpace();
        Object value = parser.value();
        parser.skipSpace();
        if (parser.pos != text.length()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    private Object value() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        switch (text.charAt(pos)) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipSpace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (!peek('"')) {
                throw error("expected string key");
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            map.put(key, value());
            skipSpace();
            if (peek(',')) {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value());
            skipSpace();
            if (peek(',')) {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("bad unicode escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException bad) {
                        throw error("bad unicode escape");
                    }
                    pos += 4;
                    break;
                default: out.append(escaped); // quote, backslash, slash
            }
        }
        throw error("unterminated string");
    }

    private Double number() {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException bad) {
            pos = start;
            throw error("unexpected character");
        }
    }

    private Object literal(String word, Object result) {
        if (!text.startsWith(word, pos)) {
            throw error("unexpected character");
        }
        pos += word.length();
        return result;
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("invalid JSON at offset " + pos + ": " + what);
    }
}
