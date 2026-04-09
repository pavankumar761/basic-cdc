package com.pavan.order_service.service.impl;

import com.pavan.order_service.entity.Order;
import com.pavan.order_service.repository.OrderRepository;
import com.pavan.order_service.service.IOrderService;
import org.springframework.stereotype.Service;

import java.security.InvalidParameterException;

/**
 * @author : Pavan Kumar
 * @created : 09/04/26, Thursday
 */

@Service
public class OrderService implements IOrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    @Override
    public Order updateOrderStatus(Long orderId, Order.ORDER_STATUS status) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new InvalidParameterException("Invalid order Id"));

        order.setStatus(status);
        return orderRepository.save(order);
    }
}
