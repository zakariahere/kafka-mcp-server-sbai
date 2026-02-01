package com.elzakaria.kafkamcpsbai.tool;

import com.elzakaria.kafkamcpsbai.dto.*;
import com.elzakaria.kafkamcpsbai.service.KafkaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaToolProvider {

    private final KafkaService kafkaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Topic Management ====================

    @Tool(description = "List all Kafka topics in the cluster. Returns a list of topic names.")
    public String listTopics() {
        try {
            List<String> topics = kafkaService.listTopics();
            return toJson(Map.of("topics", topics, "count", topics.size()));
        } catch (Exception e) {
            log.error("Failed to list topics", e);
            return errorResponse("Failed to list topics: " + e.getMessage());
        }
    }

    @Tool(description = "Get detailed information about a specific Kafka topic including partitions, replicas, and configurations.")
    public String describeTopic(
            @ToolParam(description = "The name of the topic to describe") String topicName) {
        try {
            TopicInfo topicInfo = kafkaService.describeTopic(topicName);
            return toJson(topicInfo);
        } catch (Exception e) {
            log.error("Failed to describe topic {}", topicName, e);
            return errorResponse("Failed to describe topic '" + topicName + "': " + e.getMessage());
        }
    }

    @Tool(description = "Create a new Kafka topic with the specified configuration.")
    public String createTopic(
            @ToolParam(description = "The name of the topic to create") String topicName,
            @ToolParam(description = "Number of partitions for the topic (default: 1)") Integer partitions,
            @ToolParam(description = "Replication factor for the topic (default: 1)") Integer replicationFactor) {
        try {
            int numPartitions = partitions != null ? partitions : 1;
            short replFactor = replicationFactor != null ? replicationFactor.shortValue() : 1;
            String result = kafkaService.createTopic(topicName, numPartitions, replFactor);
            return toJson(Map.of("success", true, "message", result));
        } catch (Exception e) {
            log.error("Failed to create topic {}", topicName, e);
            return errorResponse("Failed to create topic '" + topicName + "': " + e.getMessage());
        }
    }

    @Tool(description = "Delete a Kafka topic. WARNING: This operation is irreversible and will delete all messages in the topic.")
    public String deleteTopic(
            @ToolParam(description = "The name of the topic to delete") String topicName) {
        try {
            String result = kafkaService.deleteTopic(topicName);
            return toJson(Map.of("success", true, "message", result));
        } catch (Exception e) {
            log.error("Failed to delete topic {}", topicName, e);
            return errorResponse("Failed to delete topic '" + topicName + "': " + e.getMessage());
        }
    }

    // ==================== Message Production ====================

    @Tool(description = "Send a message to a Kafka topic. Returns the partition and offset where the message was written.")
    public String produceMessage(
            @ToolParam(description = "The topic to send the message to") String topicName,
            @ToolParam(description = "The message value/payload to send") String message,
            @ToolParam(description = "Optional message key for partitioning") String key,
            @ToolParam(description = "Optional headers as JSON object (e.g., {\"header1\": \"value1\"})") String headersJson) {
        try {
            Map<String, String> headers = null;
            if (headersJson != null && !headersJson.isBlank()) {
                headers = objectMapper.readValue(headersJson, Map.class);
            }
            ProduceResult result = kafkaService.produceMessage(topicName, key, message, headers);
            return toJson(result);
        } catch (Exception e) {
            log.error("Failed to produce message to topic {}", topicName, e);
            return errorResponse("Failed to produce message: " + e.getMessage());
        }
    }

    // ==================== Message Consumption ====================

    @Tool(description = "Consume messages from a Kafka topic. Creates a temporary consumer group to read messages.")
    public String consumeMessages(
            @ToolParam(description = "The topic to consume messages from") String topicName,
            @ToolParam(description = "Maximum number of messages to consume (default: 10)") Integer maxMessages,
            @ToolParam(description = "Whether to read from the beginning of the topic (default: true)") Boolean fromBeginning,
            @ToolParam(description = "Timeout in seconds to wait for messages (default: 10)") Integer timeoutSeconds) {
        try {
            int max = maxMessages != null ? maxMessages : 10;
            boolean fromStart = fromBeginning != null ? fromBeginning : true;
            int timeout = timeoutSeconds != null ? timeoutSeconds : 10;

            List<KafkaMessage> messages = kafkaService.consumeMessages(
                    topicName, max, fromStart, Duration.ofSeconds(timeout));

            return toJson(Map.of(
                    "topic", topicName,
                    "messagesReturned", messages.size(),
                    "messages", messages
            ));
        } catch (Exception e) {
            log.error("Failed to consume messages from topic {}", topicName, e);
            return errorResponse("Failed to consume messages: " + e.getMessage());
        }
    }

    @Tool(description = "Peek at messages from a specific partition and offset without committing. Useful for inspecting messages at a known location.")
    public String peekMessages(
            @ToolParam(description = "The topic to peek messages from") String topicName,
            @ToolParam(description = "The partition number to read from") int partition,
            @ToolParam(description = "The offset to start reading from") long offset,
            @ToolParam(description = "Number of messages to read (default: 5)") Integer count) {
        try {
            int numMessages = count != null ? count : 5;
            List<KafkaMessage> messages = kafkaService.peekMessages(topicName, partition, offset, numMessages);

            return toJson(Map.of(
                    "topic", topicName,
                    "partition", partition,
                    "startOffset", offset,
                    "messagesReturned", messages.size(),
                    "messages", messages
            ));
        } catch (Exception e) {
            log.error("Failed to peek messages from topic {} partition {} offset {}", topicName, partition, offset, e);
            return errorResponse("Failed to peek messages: " + e.getMessage());
        }
    }

    // ==================== Consumer Group Management ====================

    @Tool(description = "List all consumer groups in the Kafka cluster.")
    public String listConsumerGroups() {
        try {
            List<String> groups = kafkaService.listConsumerGroups();
            return toJson(Map.of("consumerGroups", groups, "count", groups.size()));
        } catch (Exception e) {
            log.error("Failed to list consumer groups", e);
            return errorResponse("Failed to list consumer groups: " + e.getMessage());
        }
    }

    @Tool(description = "Get detailed information about a consumer group including members and their partition assignments.")
    public String describeConsumerGroup(
            @ToolParam(description = "The consumer group ID to describe") String groupId) {
        try {
            ConsumerGroupInfo groupInfo = kafkaService.describeConsumerGroup(groupId);
            return toJson(groupInfo);
        } catch (Exception e) {
            log.error("Failed to describe consumer group {}", groupId, e);
            return errorResponse("Failed to describe consumer group '" + groupId + "': " + e.getMessage());
        }
    }

    // ==================== Cluster Information ====================

    @Tool(description = "Get information about the Kafka cluster including broker details and controller.")
    public String describeCluster() {
        try {
            ClusterInfo clusterInfo = kafkaService.describeCluster();
            return toJson(clusterInfo);
        } catch (Exception e) {
            log.error("Failed to describe cluster", e);
            return errorResponse("Failed to describe cluster: " + e.getMessage());
        }
    }

    // ==================== Utility Methods ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }

    private String errorResponse(String message) {
        return toJson(Map.of("success", false, "error", message));
    }
}
