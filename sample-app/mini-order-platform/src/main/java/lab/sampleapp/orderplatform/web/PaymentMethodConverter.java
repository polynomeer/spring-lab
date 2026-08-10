package lab.sampleapp.orderplatform.web;

import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import lab.sampleapp.orderplatform.payment.PaymentMethod;

/**
 * BANK_TRANSFER는 PaymentMethod enum 상수로는 유효하지만 아직 어떤 PaymentGateway도
 * 구현하지 않은 값이다(Phase 1의 "등록되지 않은 결제 수단" 경계 테스트 참고) - 이 Converter는
 * 그 사실을 웹 계층에서 먼저 걸러 서비스 계층까지 내려가서 IllegalArgumentException으로
 * 실패하는 대신 요청 자체를 400으로 거절하려는 것이다.
 *
 * <p><b>직접 겪은 함정</b>: 처음엔 Converter&lt;String, PaymentMethod&gt;로 만들어 대상
 * 타입을 PaymentMethod(순수 enum)로 그대로 두면 될 거라 예상했다 - 실제로 GenericConversionService
 * 단독으로 테스트하면 그 예상이 맞다(더 구체적인 (String,PaymentMethod) 등록이
 * StringToEnumConverterFactory의 (String,Enum) 등록보다 우선 적용된다). 하지만 실제
 * @RequestParam 바인딩 경로(MockMvc로 직접 재현)에서는 통하지 않았다 - Spring의
 * TypeConverterDelegate#convertIfNecessary()는 ConversionService가 던진 예외를 즉시
 * 전파하지 않고 일단 붙잡아 둔 뒤, "대상 타입이 Enum이고 값이 String이면 java.lang.Enum#valueOf()로
 * 한 번 더 시도한다"는 오래된(ConversionService보다 먼저 있던) 하위 호환 fallback을 마지막에
 * 실행한다 - 그 fallback이 조용히 성공해 버려서 우리 Converter의 거절이 통째로 무시된다.
 * 그래서 이 Converter의 대상 타입을 PaymentMethod가 아니라 이 fallback이 아예 적용될 수 없는
 * {@link PaymentMethodParam}(순수 웹 계층 래퍼, enum이 아님)로 바꿨다.
 */
@Component
public class PaymentMethodConverter implements Converter<String, PaymentMethodParam> {

    private static final Set<PaymentMethod> SUPPORTED = Set.of(PaymentMethod.CARD, PaymentMethod.POINT);

    @Override
    public PaymentMethodParam convert(String source) {
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedPaymentMethodException(source);
        }
        if (!SUPPORTED.contains(method)) {
            throw new UnsupportedPaymentMethodException(source);
        }
        return new PaymentMethodParam(method);
    }
}
