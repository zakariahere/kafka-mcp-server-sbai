package com.elzakaria.kafkamcpsbai;

import com.elzakaria.kafkamcpsbai.service.KafkaService;
import com.elzakaria.kafkamcpsbai.tool.KafkaToolProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KafkaMcpSbaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaMcpSbaiApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider kafkaTools(KafkaToolProvider kafkaToolProvider) {
        return MethodToolCallbackProvider.builder().toolObjects(kafkaToolProvider).build();
    }
}
