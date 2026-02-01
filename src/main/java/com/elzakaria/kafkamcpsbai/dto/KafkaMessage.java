package com.elzakaria.kafkamcpsbai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class KafkaMessage {
    private String topic;
    private int partition;
    private long offset;
    private String key;
    private String value;
    private long timestamp;
    private Map<String, String> headers;
}
