package foodassistant.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text == null ? "" : text);
        Object value = parser.parseValue();
        parser.skip();
        if (!parser.done()) {
            throw new IllegalArgumentException("Unexpected trailing JSON");
        }
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append('"');
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                switch (ch) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            out.append(String.format("\\u%04x", (int) ch));
                        } else {
                            out.append(ch);
                        }
                    }
                }
            }
            out.append('"');
        } else if (value instanceof Number number) {
            out.append(number.toString());
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(out, list.get(i));
            }
            out.append(']');
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getSimpleName());
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object parseValue() {
            skip();
            if (done()) {
                throw new IllegalArgumentException("Expected JSON");
            }
            return switch (text.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            index++;
            Map<String, Object> object = new LinkedHashMap<>();
            skip();
            if (peek('}')) {
                index++;
                return object;
            }
            while (true) {
                skip();
                if (done() || text.charAt(index) != '"') {
                    throw new IllegalArgumentException("Expected a JSON string key");
                }
                String key = parseString();
                skip();
                expect(':');
                object.put(key, parseValue());
                skip();
                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            index++;
            List<Object> list = new ArrayList<>();
            skip();
            if (peek(']')) {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skip();
                if (peek(']')) {
                    index++;
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            index++;
            StringBuilder out = new StringBuilder();
            while (!done()) {
                char ch = text.charAt(index++);
                if (ch == '"') {
                    return out.toString();
                }
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                if (done()) {
                    break;
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw new IllegalArgumentException("Bad unicode escape");
                        }
                        int code = Integer.parseInt(text.substring(index, index + 4), 16);
                        out.append((char) code);
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape");
                }
            }
            throw new IllegalArgumentException("Unclosed string");
        }

        private Object parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (!done() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (peek('.')) {
                index++;
                while (!done() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index == start || (text.charAt(start) == '-' && index == start + 1)) {
                throw new IllegalArgumentException("Expected a number");
            }
            return Double.valueOf(text.substring(start, index));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw new IllegalArgumentException("Expected " + word);
            }
            index += word.length();
            return value;
        }

        private void skip() {
            while (!done() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private void expect(char ch) {
            if (!peek(ch)) {
                throw new IllegalArgumentException("Expected " + ch);
            }
            index++;
        }

        private boolean peek(char ch) {
            return !done() && text.charAt(index) == ch;
        }

        private boolean done() {
            return index >= text.length();
        }
    }
}
