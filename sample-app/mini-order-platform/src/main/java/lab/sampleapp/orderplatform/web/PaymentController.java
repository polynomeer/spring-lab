package lab.sampleapp.orderplatform.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.payment.PaymentProcessingService;
import lab.sampleapp.orderplatform.payment.PaymentRequest;
import lab.sampleapp.orderplatform.payment.PaymentResult;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentProcessingService paymentProcessingService;

    public PaymentController(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    // actor 파라미터의 값 자체는 쓰지 않지만, @CurrentMember가 실행되면서 CurrentActor를
    // 채워야 아래 refund() 호출이 통과하는 Phase 2의 @RequiresRole(ADMIN) 검사가 그 값을
    // 볼 수 있다 - 파라미터를 선언하지 않으면 이 리졸버 자체가 호출되지 않는다.
    @PostMapping("/refund")
    public PaymentResult refund(@CurrentMember CurrentActor.Actor actor, @RequestBody RefundRequest request) {
        return paymentProcessingService.refund(new PaymentRequest(request.memberId(), request.amountWon()));
    }
}
