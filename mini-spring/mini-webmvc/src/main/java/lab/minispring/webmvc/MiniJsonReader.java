package lab.minispring.webmvc;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

// MiniJsonWriter의 반대편 - HttpMessageConverter의 극단적 축소판이다. record 하나로만
// 역직렬화할 수 있다(중첩 record는 재귀로 지원하지만, 리스트/맵/제네릭/커스텀 역직렬화
// 규칙은 다루지 않는다) - MiniJsonWriter가 record만 직렬화하는 것과 대칭을 맞췄다. 실제
// Jackson 기반 역직렬화는 이번 주 real-Spring 실험(project 25/26)에서 이미 다뤘다 - 여기서는
// "요청 본문을 읽어서 타입이 있는 인자로 바꾼다"는 ArgumentResolver의 역할 자체에 집중한다.
final class MiniJsonReader {

    private final String json;
    private int pos;

    private MiniJsonReader(String json) {
        this.json = json;
        this.pos = 0;
    }

    static <T> T read(String json, Class<T> targetType) {
        MiniJsonReader reader = new MiniJsonReader(json);
        Object value = reader.readValue(targetType);
        reader.skipWhitespace();
        if (reader.pos != json.length()) {
            throw new IllegalArgumentException("unexpected trailing content after JSON value at position " + reader.pos);
        }
        return targetType.cast(value);
    }

    private Object readValue(Class<?> targetType) {
        skipWhitespace();
        char c = peek();
        if (c == '{') {
            if (!targetType.isRecord()) {
                throw new IllegalArgumentException("cannot deserialize a JSON object into " + targetType);
            }
            return readRecord(targetType);
        }
        if (c == '"') {
            return readString();
        }
        if (c == 't' || c == 'f') {
            return readBoolean();
        }
        if (c == 'n') {
            readLiteral("null");
            return null;
        }
        return readNumber(targetType);
    }

    private Object readRecord(Class<?> recordType) {
        RecordComponent[] components = recordType.getRecordComponents();
        Map<String, Class<?>> typesByName = new HashMap<>();
        for (RecordComponent component : components) {
            typesByName.put(component.getName(), component.getType());
        }

        Map<String, Object> valuesByName = new HashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() != '}') {
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Class<?> fieldType = typesByName.get(key);
                if (fieldType == null) {
                    // 대상 record에 없는 필드는 조용히 건너뛴다 - Jackson의 기본값(알 수 없는
                    // 필드는 실패)과 다른, 의도적으로 관대한 선택이다.
                    skipValue();
                } else {
                    valuesByName.put(key, readValue(fieldType));
                }
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    break;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("expected ',' or '}' but found '" + next + "'");
                }
            }
        } else {
            pos++; // 빈 객체 "{}"
        }

        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            Object value = valuesByName.get(components[i].getName());
            arguments[i] = (value == null && parameterTypes[i].isPrimitive())
                    ? defaultPrimitiveValue(parameterTypes[i])
                    : value;
        }
        try {
            Constructor<?> constructor = recordType.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to construct " + recordType + " from JSON", ex);
        }
    }

    private Object readNumber(Class<?> targetType) {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < json.length() && isNumberChar(json.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            throw new IllegalArgumentException("expected a JSON value at position " + pos);
        }
        String raw = json.substring(start, pos);
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(raw);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(raw);
        }
        if (targetType == Object.class) {
            // skipValue()가 알 수 없는 필드를 버릴 때만 이 경로를 탄다 - 정확한 타입은
            // 어차피 아무도 쓰지 않으므로 아무 숫자 타입으로나 파싱해서 버리면 된다.
            return Double.parseDouble(raw);
        }
        throw new IllegalArgumentException("unsupported numeric field type: " + targetType);
    }

    private boolean isNumberChar(char c) {
        return Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                sb.append(readEscape());
            } else {
                sb.append(c);
            }
        }
    }

    private char readEscape() {
        char c = next();
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'u' -> readUnicodeEscape();
            default -> throw new IllegalArgumentException("unsupported escape sequence: \\" + c);
        };
    }

    private char readUnicodeEscape() {
        String hex = json.substring(pos, pos + 4);
        pos += 4;
        return (char) Integer.parseInt(hex, 16);
    }

    private Boolean readBoolean() {
        if (peek() == 't') {
            readLiteral("true");
            return Boolean.TRUE;
        }
        readLiteral("false");
        return Boolean.FALSE;
    }

    private void readLiteral(String literal) {
        if (!json.regionMatches(pos, literal, 0, literal.length())) {
            throw new IllegalArgumentException("expected literal '" + literal + "' at position " + pos);
        }
        pos += literal.length();
    }

    // 대상 record에 없는 필드의 값을 읽지 않고 그냥 건너뛴다 - 문자열 안의 중괄호/대괄호는
    // 세지 않도록 문자열은 반드시 readString()으로 통째로 소비한다.
    private void skipValue() {
        skipWhitespace();
        char c = peek();
        if (c == '{' || c == '[') {
            char open = c;
            char close = (open == '{') ? '}' : ']';
            int depth = 0;
            do {
                char current = next();
                if (current == '"') {
                    pos--;
                    readString();
                } else if (current == open) {
                    depth++;
                } else if (current == close) {
                    depth--;
                }
            } while (depth > 0);
        } else if (c == '"') {
            readString();
        } else if (c == 't' || c == 'f') {
            readBoolean();
        } else if (c == 'n') {
            readLiteral("null");
        } else {
            readNumber(Object.class);
        }
    }

    private static Object defaultPrimitiveValue(Class<?> primitiveType) {
        if (primitiveType == int.class) {
            return 0;
        }
        if (primitiveType == long.class) {
            return 0L;
        }
        if (primitiveType == double.class) {
            return 0.0d;
        }
        if (primitiveType == boolean.class) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("unsupported primitive field type: " + primitiveType);
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= json.length()) {
            throw new IllegalArgumentException("unexpected end of JSON input");
        }
        return json.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        char actual = next();
        if (actual != expected) {
            throw new IllegalArgumentException("expected '" + expected + "' but found '" + actual + "' at position " + (pos - 1));
        }
    }
}
