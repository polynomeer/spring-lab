package lab.sampleapp.orderplatform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lab.sampleapp.orderplatform.aop.AccessDeniedException;
import lab.sampleapp.orderplatform.order.OrderNotCancellableException;
import lab.sampleapp.orderplatform.order.OrderNotFoundException;
import lab.sampleapp.orderplatform.order.PaymentFailedException;

/**
 * 이 캡스톤 전역에서 던져질 수 있는 예외를 한 곳에 모아 일관된 {@link ErrorResponse} 형태로
 * 매핑한다 - Phase 2(AOP)의 AccessDeniedException, Phase 3(트랜잭션)의
 * PaymentFailedException/OrderNotCancellableException/UnexpectedRollbackException까지
 * 전부 여기서 HTTP 상태 코드를 얻는다. 각 예외는 어느 Phase에서 왔든 원래 그 Phase의
 * 목적(도메인 규칙 위반, 권한 거부, 트랜잭션 무결성)만 신경 쓰면 됐고, "이게 HTTP로 나갈 때
 * 몇 번이어야 하는가"는 순전히 이 웹 계층의 관심사로 분리돼 있다.
 */
@RestControllerAdvice
public class OrderExceptionHandlers {

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    ResponseEntity<ErrorResponse> handlePaymentFailed(PaymentFailedException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse("PAYMENT_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ACCESS_DENIED", ex.getMessage()));
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    ResponseEntity<ErrorResponse> handleNotCancellable(OrderNotCancellableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ORDER_NOT_CANCELLABLE", ex.getMessage()));
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    ResponseEntity<ErrorResponse> handleUnexpectedRollback(UnexpectedRollbackException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TRANSACTION_ROLLED_BACK", ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedPaymentMethodException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMethod(UnsupportedPaymentMethodException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("UNSUPPORTED_PAYMENT_METHOD", ex.getMessage()));
    }

    // PaymentMethodConverter가 던진 UnsupportedPaymentMethodException은 여기 직접 도달하지
    // 않는다 - GenericConversionService#convert()가 Converter의 예외를 항상
    // ConversionFailedException으로 감싸고, RequestParamMethodArgumentResolver가 그걸 다시
    // MethodArgumentTypeMismatchException으로 감싸서 던지기 때문이다(PaymentMethodConverterTest에서
    // 직접 확인). 그래서 원인 체인을 들여다봐서 우리 예외가 있으면 그 메시지를 쓰고, 없으면
    // (예: enum 자체에 없는 값) 일반적인 메시지로 대체한다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause instanceof UnsupportedPaymentMethodException
                ? cause.getMessage()
                : "invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("UNSUPPORTED_PAYMENT_METHOD", message));
    }

    @ExceptionHandler(MissingCurrentMemberException.class)
    ResponseEntity<ErrorResponse> handleMissingCurrentMember(MissingCurrentMemberException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHENTICATED", ex.getMessage()));
    }
}
