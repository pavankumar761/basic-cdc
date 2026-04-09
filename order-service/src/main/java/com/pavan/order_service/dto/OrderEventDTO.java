package com.pavan.order_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author : Pavan Kumar
 * @created : 10/04/26, Friday
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEventDTO {
    private Long id;
    private BigDecimal amount;
    private String product_name;
    private String status;
    private Long user_id;

    // Debezium Metadata
    @JsonProperty("__op")
    private String operation;

    @JsonProperty("__table")
    private String tableName;
}