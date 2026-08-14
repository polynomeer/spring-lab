package lab.experiments.conversion;

// ConversionService의 도움 없이는 "3,4" 같은 문자열에서 만들어질 방법이 전혀 없는 타입 -
// 어떤 경로로도 기본 PropertyEditor가 알지 못하는 커스텀 타입이라는 것이 실험의 핵심이다.
public record Point(int x, int y) {
}
