package lab.sampleapp.orderplatform.web;

import lab.sampleapp.orderplatform.payment.PaymentMethod;

/**
 * PaymentMethod(도메인 enum) 대신 이 웹 전용 래퍼 타입을 @RequestParam 대상으로 쓴다 -
 * 직접 겪은 이유는 PaymentMethodConverter의 클래스 주석에 적어 뒀다: PaymentMethod를 그대로
 * 대상으로 쓰면 우리 Converter가 예외를 던져도 Spring이 그 실패를 조용히 삼키고 JDK의
 * Enum.valueOf()로 재시도해 버린다. 이 래퍼는 그 문제가 애초에 발생할 수 없는, Spring이
 * 아무 기본 변환도 미리 알지 못하는 타입이다.
 */
public record PaymentMethodParam(PaymentMethod value) {
}
