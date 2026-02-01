package com.elzakaria.kafkamcpsbai.service;

import com.elzakaria.kafkamcpsbai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaService {

    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private AdminClient getAdminClient() {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    public List<String> listTopics() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            return new ArrayList<>(adminClient.listTopics().names().get());
        }
    }

    public TopicInfo describeTopic(String topicName) throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            // Get topic description
            TopicDescription description = adminClient.describeTopics(List.of(topicName))
                    .topicNameValues()
                    .get(topicName)
                    .get();

            // Get topic configs
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            Config config = adminClient.describeConfigs(List.of(configResource))
                    .values()
                    .get(configResource)
                    .get();

            Map<String, String> configMap = config.entries().stream()
                    .filter(entry -> !entry.isDefault())
                    .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value));

            List<TopicInfo.PartitionInfo> partitions = description.partitions().stream()
                    .map(p -> TopicInfo.PartitionInfo.builder()
                            .partition(p.partition())
                            .leader(p.leader() != null ? p.leader().id() : -1)
                            .replicas(p.replicas().stream().map(Node::id).toList())
                            .inSyncReplicas(p.isr().stream().map(Node::id).toList())
                            .build())
                    .toList();

            return TopicInfo.builder()
                    .name(description.name())
                    .partitionCount(description.partitions().size())
                    .partitions(partitions)
                    .configs(configMap)
                    .build();
        }
    }

    public String createTopic(String topicName, int partitions, short replicationFactor)
            throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
            adminClient.createTopics(List.of(newTopic)).all().get();
            return "Topic '" + topicName + "' created successfully with " + partitions +
                   " partition(s) and replication factor " + replicationFactor;
        }
    }

    public String deleteTopic(String topicName) throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            adminClient.deleteTopics(List.of(topicName)).all().get();
            return "Topic '" + topicName + "' deleted successfully";
        }
    }

    public ProduceResult produceMessage(String topic, String key, String value, Map<String, String> headers) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

            if (headers != null) {
                headers.forEach((k, v) ->
                    record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
            }

            RecordMetadata metadata = kafkaTemplate.send(record).get().getRecordMetadata();

            return ProduceResult.builder()
                    .topic(metadata.topic())
                    .partition(metadata.partition())
                    .offset(metadata.offset())
                    .timestamp(metadata.timestamp())
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to produce message to topic {}", topic, e);
            return ProduceResult.builder()
                    .topic(topic)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public List<KafkaMessage> consumeMessages(String topic, int maxMessages, boolean fromBeginning,
                                               Duration timeout) {
        Properties props = new Properties();
        props.putAll(kafkaAdmin.getConfigurationProperties());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-mcp-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, fromBeginning ? "earliest" : "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                  "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                  "org.apache.kafka.common.serialization.StringDeserializer");

        List<KafkaMessage> messages = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));

            long endTime = System.currentTimeMillis() + timeout.toMillis();

            while (messages.size() < maxMessages && System.currentTimeMillis() < endTime) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                for (ConsumerRecord<String, String> record : records) {
                    if (messages.size() >= maxMessages) break;

                    Map<String, String> headerMap = new HashMap<>();
                    for (Header header : record.headers()) {
                        headerMap.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
                    }

                    messages.add(KafkaMessage.builder()
                            .topic(record.topic())
                            .partition(record.partition())
                            .offset(record.offset())
                            .key(record.key())
                            .value(record.value())
                            .timestamp(record.timestamp())
                            .headers(headerMap)
                            .build());
                }
            }
        }

        return messages;
    }

    public List<KafkaMessage> peekMessages(String topic, int partition, long offset, int count) {
        Properties props = new Properties();
        props.putAll(kafkaAdmin.getConfigurationProperties());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-mcp-peek-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                  "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                  "org.apache.kafka.common.serialization.StringDeserializer");

        List<KafkaMessage> messages = new ArrayList<>();
        TopicPartition tp = new TopicPartition(topic, partition);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.assign(List.of(tp));
            consumer.seek(tp, offset);

            while (messages.size() < count) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                if (records.isEmpty()) break;

                for (ConsumerRecord<String, String> record : records) {
                    if (messages.size() >= count) break;

                    Map<String, String> headerMap = new HashMap<>();
                    for (Header header : record.headers()) {
                        headerMap.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
                    }

                    messages.add(KafkaMessage.builder()
                            .topic(record.topic())
                            .partition(record.partition())
                            .offset(record.offset())
                            .key(record.key())
                            .value(record.value())
                            .timestamp(record.timestamp())
                            .headers(headerMap)
                            .build());
                }
            }
        }

        return messages;
    }

    @SuppressWarnings("removal")
    public List<String> listConsumerGroups() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            // Using deprecated API - will migrate when Kafka provides stable replacement
            return adminClient.listConsumerGroups().all().get().stream()
                    .map(ConsumerGroupListing::groupId)
                    .toList();
        }
    }

    public ConsumerGroupInfo describeConsumerGroup(String groupId) throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            ConsumerGroupDescription description = adminClient.describeConsumerGroups(List.of(groupId))
                    .describedGroups()
                    .get(groupId)
                    .get();

            List<ConsumerGroupInfo.MemberInfo> members = description.members().stream()
                    .map(member -> {
                        Map<String, List<Integer>> assignmentsByTopic = new HashMap<>();
                        member.assignment().topicPartitions().forEach(tp -> {
                            assignmentsByTopic.computeIfAbsent(tp.topic(), k -> new ArrayList<>())
                                    .add(tp.partition());
                        });

                        List<ConsumerGroupInfo.TopicPartitionAssignment> assignments = assignmentsByTopic.entrySet()
                                .stream()
                                .map(e -> ConsumerGroupInfo.TopicPartitionAssignment.builder()
                                        .topic(e.getKey())
                                        .partitions(e.getValue())
                                        .build())
                                .toList();

                        return ConsumerGroupInfo.MemberInfo.builder()
                                .memberId(member.consumerId())
                                .clientId(member.clientId())
                                .host(member.host())
                                .assignments(assignments)
                                .build();
                    })
                    .toList();

            return ConsumerGroupInfo.builder()
                    .groupId(description.groupId())
                    .state(description.state().toString())
                    .coordinator(description.coordinator() != null ?
                                 description.coordinator().host() + ":" + description.coordinator().port() : null)
                    .partitionAssignor(description.partitionAssignor())
                    .members(members)
                    .build();
        }
    }

    public ClusterInfo describeCluster() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = getAdminClient()) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();

            String clusterId = clusterResult.clusterId().get();
            Node controller = clusterResult.controller().get();
            Collection<Node> nodes = clusterResult.nodes().get();

            ClusterInfo.BrokerInfo controllerInfo = ClusterInfo.BrokerInfo.builder()
                    .id(controller.id())
                    .host(controller.host())
                    .port(controller.port())
                    .rack(controller.rack())
                    .build();

            List<ClusterInfo.BrokerInfo> brokers = nodes.stream()
                    .map(node -> ClusterInfo.BrokerInfo.builder()
                            .id(node.id())
                            .host(node.host())
                            .port(node.port())
                            .rack(node.rack())
                            .build())
                    .toList();

            return ClusterInfo.builder()
                    .clusterId(clusterId)
                    .controller(controllerInfo)
                    .brokers(brokers)
                    .build();
        }
    }
}
