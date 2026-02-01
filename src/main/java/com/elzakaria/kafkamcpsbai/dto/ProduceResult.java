package com.elzakaria.kafkamcpsbai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProduceResult {
    private String topic;
    private int partition;
    private long offset;
    private long timestamp;
    private boolean success;
    private String errorMessage;
}
