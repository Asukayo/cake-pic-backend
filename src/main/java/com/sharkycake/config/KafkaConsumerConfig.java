package com.sharkycake.config;


import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    // Spring Boot 会将这个错误处理器应用到默认的监听容器。
    // 普通可重试异常最多执行 首次 + 2 次重试；
    // 部分框架认定不可重试的异常会直接转入死信。死信发送失败时，框架会继续尝试恢复。
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        // 重试次数耗尽之后，把原来的消息转发到死信Topic中
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(
                                "picture.cleanup.dlt",
                                record.partition()
                        )
        );
        // 死信发送失败时也要抛出一场，不能当作恢复成功
        recoverer.setFailIfSendResultIsError(true);

        // 每次间隔 3 秒，额外重试 2 次
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(3000L, 2L)
        );
    }
}
