package lab.minispring.webmvc;

// 실제 Spring의 ConversionService에 대응하는, String -> 몇 가지 흔한 타입만 다루는 축소판.
final class TypeConversion {

    private TypeConversion() {
    }

    static Object convert(String rawValue, Class<?> targetType) {
        if (rawValue == null) {
            return null;
        }
        if (targetType == String.class) {
            return rawValue;
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(rawValue);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(rawValue);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(rawValue);
        }
        throw new IllegalArgumentException("unsupported target type: " + targetType);
    }
}
