package com.sharkycake.KafkaTest;

import com.sharkycake.picture.cleanup.config.KafkaTopicConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = KafkaIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
public class KafkaIntegrationTest {

    private static final String BUSINESS_TOPIC = "picture.cleanup.requested";
    private static final String TEST_TOPIC = "picture.cleanup.test";

    @Resource
    private KafkaAdmin kafkaAdmin;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    // 只加载本次测试需要的 Kafka 配置
    @Configuration(proxyBeanMethods = false)
    @Import({KafkaAutoConfiguration.class, KafkaTopicConfig.class})
    static class TestConfig {

        @Bean
        public NewTopic kafkaTestTopic() {
            return TopicBuilder.name(TEST_TOPIC)
                    .partitions(3)
                    .replicas(3)
                    .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                    .build();
        }
    }

    /**
     * 检查集群中实际生效的 Topic 配置
     */
    @Test
    void testTopicConfiguration() throws Exception {
        try (AdminClient client =
                     AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            TopicDescription topic = client
                    .describeTopics(Collections.singletonList(BUSINESS_TOPIC))
                    .values()
                    .get(BUSINESS_TOPIC)
                    .get(10, TimeUnit.SECONDS);

            assertEquals(3, topic.partitions().size(), "分区数应该为 3");

            for (TopicPartitionInfo partition : topic.partitions()) {
                assertEquals(3, partition.replicas().size(),
                        "分区 " + partition.partition() + " 的副本数应该为 3");

                System.out.printf("分区=%d，副本=%s，同步副本=%s%n",
                        partition.partition(),
                        partition.replicas(),
                        partition.isr());
            }

            ConfigResource resource = new ConfigResource(
                    ConfigResource.Type.TOPIC, BUSINESS_TOPIC);

            Config config = client
                    .describeConfigs(Collections.singletonList(resource))
                    .values()
                    .get(resource)
                    .get(10, TimeUnit.SECONDS);

            assertEquals("2",
                    config.get(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG).value(),
                    "最小同步副本数应该为 2");
        }
    }

    /**
     * 向独立的测试 Topic 发送一条消息
     */
    @Test
    void testSendMessage() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"eventId\":\"" + eventId + "\"}";

        SendResult<String, String> result = kafkaTemplate
                .send(TEST_TOPIC, eventId, payload)
                .get(10, TimeUnit.SECONDS);

        assertEquals(TEST_TOPIC, result.getRecordMetadata().topic());
        assertTrue(result.getRecordMetadata().offset() >= 0);

        System.out.printf("发送成功：topic=%s，partition=%d，offset=%d%n",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }
}