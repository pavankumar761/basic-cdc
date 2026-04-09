package com.pavan.order_service.service;

import com.pavan.order_service.entity.Order;

/**
 * @author : Pavan Kumar
 * @created : 09/04/26, Thursday
 */

public interface IOrderService {

    Order createOrder(Order order);

    Order updateOrderStatus(Long orderId, Order.ORDER_STATUS status);
}
