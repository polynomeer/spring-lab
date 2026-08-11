package lab.sampleapp.orderplatform.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.order.CancellationOutcome;
import lab.sampleapp.orderplatform.order.Order;
import lab.sampleapp.orderplatform.order.OrderCancellationService;
import lab.sampleapp.orderplatform.order.OrderNotFoundException;
import lab.sampleapp.orderplatform.order.OrderPlacementService;
import lab.sampleapp.orderplatform.order.OrderRepository;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderPlacementService orderPlacementService;
    private final OrderRepository orderRepository;
    private final OrderCancellationService orderCancellationService;

    public OrderController(
            OrderPlacementService orderPlacementService,
            OrderRepository orderRepository,
            OrderCancellationService orderCancellationService) {
        this.orderPlacementService = orderPlacementService;
        this.orderRepository = orderRepository;
        this.orderCancellationService = orderCancellationService;
    }

    // method는 @RequestParam이라 PaymentMethodConverter(String -> PaymentMethodParam)를 거친다 -
    // items는 JSON 본문이라 Jackson이 처리하고, 이 Converter와는 무관하다. 가격은 요청에
    // 아예 없다 - OrderPlacementService가 Product 테이블에서 직접 조회해 계산한다.
    @PostMapping
    public Order placeOrder(
            @CurrentMember CurrentActor.Actor actor,
            @RequestParam PaymentMethodParam method,
            @RequestBody PlaceOrderRequest request) {
        return orderPlacementService.placeOrder(actor.id(), method.value(), request.items());
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @PostMapping("/{id}/cancel")
    public CancellationOutcome cancelOrder(@PathVariable long id) {
        return orderCancellationService.cancelSwallowingValidationFailure(id);
    }
}
