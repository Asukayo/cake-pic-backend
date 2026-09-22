package com.sharkycake.Schedule;


import com.sharkycake.model.entity.MessageOutbox;
import com.sharkycake.service.MessageOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 负责扫描待发送的任务
 */
@Slf4j
@Component
public class OutboxMessagePublisher {

    private final MessageOutboxService messageOutboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxMessagePublisher(MessageOutboxService messageOutboxService,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.messageOutboxService = messageOutboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 定时扫描并发送待投递消息
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingMessages() {
        // 加载数据
        List<MessageOutbox> messages = loadPendingMessages();
        for (MessageOutbox message : messages) {
            try {
                publishOne(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox 投递任务被中断，结束本轮扫描");
                return;
            } catch (Exception e) {
                // 单条处理异常不影响后续消息
                log.error("Outbox 投递处理异常，id={}, eventId={}",
                        message.getId(), message.getEventId(), e);
            }
        }
    }


    private List<MessageOutbox> loadPendingMessages() {
        return messageOutboxService.lambdaQuery()
                // 查询状态为待发送
                .eq(MessageOutbox::getSendStatus, 0)
                // 下次重试时间比当前时间早
                .le(MessageOutbox::getNextRetryTime, new Date())
                // 按照下次重试时间排序
                .orderByAsc(MessageOutbox::getNextRetryTime)
                // 相同按照id排序
                .orderByAsc(MessageOutbox::getId)
                // 一次查询100挑
                .last("LIMIT 100")
                .list();
    }


    private void publishOne(MessageOutbox messageOutbox) throws InterruptedException {
        try {
            // 参数分别是，Topic，消息key，消息正文
            // 需要等待kafka确认发送结果
            kafkaTemplate.send(
                    messageOutbox.getTopic(),
                    messageOutbox.getEventId(),
                    messageOutbox.getPayload()
            ).get(10, TimeUnit.SECONDS); // 用来等待结果
        } catch (InterruptedException e) {
            // 线程被要求停止时，保留中断信号并交给调用方处理
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            // 发送失败或等待超时，安排后续重试
            recordSendFailure(messageOutbox, e);
            return;
        }
        // 只有 Kafka 确认成功后，才标记已发送
        boolean updated = messageOutboxService.lambdaUpdate()
                .eq(MessageOutbox::getId, messageOutbox.getId())
                .eq(MessageOutbox::getSendStatus, 0)
                .set(MessageOutbox::getSendStatus, 1)
                .set(MessageOutbox::getLastError, null)
                .update();

        if (!updated) {
            throw new IllegalStateException(
                    "Kafka 已确认发送，但 Outbox 状态未更新，id=" + messageOutbox.getId()
            );
        }
    }
    // 失败时如何处理
    private void recordSendFailure(MessageOutbox outbox, Exception exception) {
        int failureCount = outbox.getRetryCount() + 1;
        // 累计失败达到 5 次，停止自动重试
        int sendStatus = failureCount >= 5 ? 2 : 0;
        String errorMessage = exception.toString();
        // lastError 字段最多保存 1000 个字符
        if (errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        boolean updated = messageOutboxService.lambdaUpdate()
                .eq(MessageOutbox::getId, outbox.getId())
                .eq(MessageOutbox::getSendStatus, 0)
                .set(MessageOutbox::getRetryCount, failureCount)
                .set(MessageOutbox::getSendStatus, sendStatus)
                .set(MessageOutbox::getLastError, errorMessage)
                .set(
                        MessageOutbox::getNextRetryTime,
                        new Date(System.currentTimeMillis() + 60_000L)
                )
                .update();
        if (!updated) {
            throw new IllegalStateException(
                    "记录 Outbox 发送失败信息时更新失败，id=" + outbox.getId(),
                    exception
            );
        }
    }


}