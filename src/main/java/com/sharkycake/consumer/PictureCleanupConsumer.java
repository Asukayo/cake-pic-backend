package com.sharkycake.consumer;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.model.entity.PictureCleanupTask;
import com.sharkycake.service.PictureCleanupTaskService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class PictureCleanupConsumer {

    @Resource
    private PictureCleanupTaskService pictureCleanupTaskService;

    // 收到消息 → 解析 eventId → processTask()
    //                           ├─ 正常返回：容器按 record 模式提交消费进度
    //                           └─ 抛出异常：重试 → 仍失败则转发死信 Topic
    @KafkaListener(
            topics = "picture.cleanup.requested",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "1"
    )
    public void consume(ConsumerRecord<String, String> record) {
        String payload = record.value();
        ThrowUtils.throwIf(StrUtil.isBlank(payload),
                ErrorCode.PARAMS_ERROR,"清理消息不能为空");
        String eventId = JSONUtil.parseObj(payload).getStr("eventId");
        if (StrUtil.isBlank(eventId)) {
            throw new IllegalArgumentException("清理消息缺少 eventId");
        }

        if (!eventId.equals(record.key())) {
            throw new IllegalArgumentException("消息 key 与 eventId 不一致");
        }
        // 一场继续向上传递，交给错误处理器
        pictureCleanupTaskService.processTask(eventId);

    }

}
