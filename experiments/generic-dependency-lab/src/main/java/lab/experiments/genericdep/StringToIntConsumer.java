package lab.experiments.genericdep;

// Converter<String, Integer>를 요구한다 - 컨테이너 안에 Converter<Integer, String>도 함께
// 있는데도, 원시 타입(Converter)이 아니라 제네릭 타입 인자까지 보고 정확히 어느 쪽인지
// 구분해야 한다.
public class StringToIntConsumer {

    private final Converter<String, Integer> converter;

    public StringToIntConsumer(Converter<String, Integer> converter) {
        this.converter = converter;
    }

    public Integer convert(String source) {
        return converter.convert(source);
    }
}
