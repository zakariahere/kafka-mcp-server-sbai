package com.elzakaria.kafkamcpsbai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TopicInfo {
    private String name;
    private int partitionCount;
    private List<PartitionInfo> partitions;
    private Map<String, String> configs;

    @Data
    @Builder
    public static class PartitionInfo {
        private int partition;
        private int leader;
        private List<Integer> replicas;
        private List<Integer> inSyncReplicas;
    }
}
