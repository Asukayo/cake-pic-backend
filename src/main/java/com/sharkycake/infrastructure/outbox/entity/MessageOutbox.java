package com.sharkycake.infrastructure.outbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * Kafka 消息待发送表
 * @TableName message_outbox
 */
@TableName(value ="message_outbox")
@Data
public class MessageOutbox implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 事件唯一标识，与清理任务的 eventId 一致，重试时不变
     */
    @TableField(value = "eventId")
    private String eventId;

    /**
     * 发送到哪个 Kafka Topic
     */
    @TableField(value = "topic")
    private String topic;

    /**
     * 消息内容，JSON 字符串
     */
    @TableField(value = "payload")
    private String payload;

    /**
     * 发送状态：0-待发送 1-已发送 2-停止自动重试
     */
    @TableField(value = "sendStatus")
    private Integer sendStatus;

    /**
     * 发送失败次数
     */
    @TableField(value = "retryCount")
    private Integer retryCount;

    /**
     * 下次允许发送的时间，首次创建时即可发送
     */
    @TableField(value = "nextRetryTime")
    private Date nextRetryTime;

    /**
     * 最近一次发送失败的原因
     */
    @TableField(value = "lastError")
    private String lastError;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "updateTime")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}