package lab.sampleapp.orderplatform.web;

import java.util.List;

import lab.sampleapp.orderplatform.order.OrderItemRequest;

public record PlaceOrderRequest(List<OrderItemRequest> items) {
}
