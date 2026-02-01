package com.elzakaria.kafkamcpsbai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClusterInfo {
    private String clusterId;
    private BrokerInfo controller;
    private List<BrokerInfo> brokers;

    @Data
    @Builder
    public static class BrokerInfo {
        private int id;
        private String host;
        private int port;
        private String rack;
    }
}
