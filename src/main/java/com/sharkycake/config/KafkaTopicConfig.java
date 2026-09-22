package com.sharkycake.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {


    @Bean
    public NewTopic pictureCleanupTopic() {
        return TopicBuilder.name("picture.cleanup.requested")
                .partitions(3)
                .replicas(3)
                // 同步副本不足 2 个时拒绝写入
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    // 死信Topic，用来保存重试后仍然处理失败的消息
    @Bean
    public NewTopic pictureCleanupDltTopic() {
        return TopicBuilder.name("picture.cleanup.dlt")
                .partitions(3)
                .replicas(3)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }
}
