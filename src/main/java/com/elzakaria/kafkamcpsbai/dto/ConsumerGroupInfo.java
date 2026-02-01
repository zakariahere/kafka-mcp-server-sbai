package com.elzakaria.kafkamcpsbai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConsumerGroupInfo {
    private String groupId;
    private String state;
    private String coordinator;
    private String partitionAssignor;
    private List<MemberInfo> members;

    @Data
    @Builder
    public static class MemberInfo {
        private String memberId;
        private String clientId;
        private String host;
        private List<TopicPartitionAssignment> assignments;
    }

    @Data
    @Builder
    public static class TopicPartitionAssignment {
        private String topic;
        private List<Integer> partitions;
    }
}
