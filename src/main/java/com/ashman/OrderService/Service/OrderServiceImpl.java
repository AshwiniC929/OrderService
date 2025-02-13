package com.ashman.OrderService.Service;


import com.ashman.OrderService.Entity.Order;
import com.ashman.OrderService.Exception.CustomException;
import com.ashman.OrderService.External.Client.PaymentService;
import com.ashman.OrderService.External.Client.ProductService;
import com.ashman.OrderService.External.Request.PaymentRequest;
import com.ashman.OrderService.External.Response.PaymentResponse;
import com.ashman.OrderService.Model.OrderRequest;
import com.ashman.OrderService.Model.OrderResponse;
import com.ashman.OrderService.Repository.OrderRepository;
import com.ashman.ProductService.Model.ProductResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;


@Service
@Log4j2
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public long placeOrder(OrderRequest orderRequest) {

        //Order Entity -> Save the data with status order created
        //call Product Service - Block Product(Reduce the Quantity)
        //Payment service -> payment -> Success ->Complete, Else-> Cancelled

        log.info("Placing Order request: {}", orderRequest);

        productService.reduceQuantity(orderRequest.getProductId(), orderRequest.getQuantity());

        log.info("Creating Order with status Created");

        Order order = Order.builder()
                .amount(orderRequest.getTotalAmount())
                .orderStatus("CREATED")
                .productId(orderRequest.getProductId())
                .orderDate(Instant.now())
                .quantity(orderRequest.getQuantity())
                .build();

        order = orderRepository.save(order);

        log.info("Calling Payment Service to Complete the Payment");

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderId(order.getId())
                .paymentMode(orderRequest.getPaymentMode())
                .amount(orderRequest.getTotalAmount())
                .build();

        String orderStatus = null;
        try {
            paymentService.doPayment(paymentRequest);
            log.info("Payment done Successfully. Changing the order Status to Placed");
            orderStatus = "PLACED";
        }catch (Exception e){
            log.info("Error Occurred in payment. Changing order Status to PAYMENT_FAILED");
            orderStatus = "PAYMENT_FAILED";

        }

        order.setOrderStatus(orderStatus);
        orderRepository.save(order);

        log.info("Order Placed Successfully with Order Id: {}", order.getId());
        return order.getId();
    }

    @Override
    public OrderResponse getOrderDetails(long orderId) {
        log.info("Get the Order details for Order Id: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(()-> new CustomException("Order Not Found for Order Id",
                        "NOT_FOUND",
                        404));

        log.info("Invoking Product Service to fetch the product for id: {}", order.getProductId());

        ProductResponse productResponse = restTemplate.getForObject(
                "http://PRODUCT-SERVICE/product/" + order.getProductId(),
                ProductResponse.class
        );

        log.info("Getting Payment information from the payment Service");

        PaymentResponse paymentResponse = restTemplate.getForObject(
                "http://PAYMENT-SERVICE/payment/order/" + order.getId(),
                PaymentResponse.class);

        OrderResponse.ProductDetails productDetails = OrderResponse.ProductDetails
                .builder()
                .productName(productResponse.getProductName())
                .productId(productResponse.getProductId())
                .build();

        OrderResponse.PaymentDetails paymentDetails = OrderResponse.PaymentDetails
                .builder()
                .paymentId(paymentResponse.getPaymentId())
                .paymentStatus(paymentResponse.getStatus())
                .paymentDate(paymentResponse.getPaymentDate())
                .paymentMode(paymentResponse.getPaymentMode())
                .build();

        OrderResponse orderResponse = OrderResponse.builder()
                .orderId(order.getId())
                .orderStatus(order.getOrderStatus())
                .amount(order.getAmount())
                .orderDate(order.getOrderDate())
                .productDetails(productDetails)
                .paymentDetails(paymentDetails)
                .build();


        return orderResponse;
    }
}
