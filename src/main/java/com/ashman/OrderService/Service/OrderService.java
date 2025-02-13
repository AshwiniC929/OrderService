package com.ashman.OrderService.Service;

import com.ashman.OrderService.Model.OrderRequest;
import com.ashman.OrderService.Model.OrderResponse;

public interface OrderService {

    long placeOrder(OrderRequest orderRequest);

    OrderResponse getOrderDetails(long orderId);
}
