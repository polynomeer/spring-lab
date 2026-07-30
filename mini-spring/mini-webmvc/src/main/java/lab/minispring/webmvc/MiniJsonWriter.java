package lab.minispring.webmvc;

import java.lang.reflect.RecordComponent;

// HttpMessageConverter의 극단적 축소판 - record 하나만 직렬화할 수 있다. 실제 JSON
// 직렬화(중첩 컬렉션, 제네릭, 커스텀 직렬화 규칙 등)는 이번 주 real-Spring 실험(project
// 25/26)에서 이미 Jackson으로 다뤘다 - 여기서는 "반환값을 바이트로 바꿔 응답에 쓴다"는
// ReturnValueHandler의 역할 자체에 집중한다.
final class MiniJsonWriter {

    private MiniJsonWriter() {
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value.getClass().isRecord()) {
            writeRecord(value, sb);
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeRecord(Object record, StringBuilder sb) {
        sb.append("{");
        RecordComponent[] components = record.getClass().getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            writeString(components[i].getName(), sb);
            sb.append(":");
            try {
                writeValue(components[i].getAccessor().invoke(record), sb);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
        sb.append("}");
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"').append(s.replace("\"", "\\\"")).append('"');
    }
}
