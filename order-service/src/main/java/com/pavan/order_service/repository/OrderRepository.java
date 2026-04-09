package com.pavan.order_service.repository;

import com.pavan.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author : Pavan Kumar
 * @created : 09/04/26, Thursday
 */

public interface OrderRepository extends JpaRepository<Order, Long> {
}
