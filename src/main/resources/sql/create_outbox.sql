CREATE TABLE IF NOT EXISTS message_outbox
(
    id              BIGINT AUTO_INCREMENT
    COMMENT '主键',

    eventId         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '事件唯一标识，与清理任务的 eventId 一致，重试时不变',

    topic           VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '发送到哪个 Kafka Topic',

    payload         TEXT NOT NULL
    COMMENT '消息内容，JSON 字符串',

    sendStatus      TINYINT NOT NULL DEFAULT 0
    COMMENT '发送状态：0-待发送 1-已发送 2-停止自动重试',

    retryCount      INT NOT NULL DEFAULT 0
    COMMENT '发送失败次数',

    nextRetryTime   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '下次允许发送的时间，首次创建时即可发送',

    lastError       VARCHAR(1000) NULL
    COMMENT '最近一次发送失败的原因',

    createTime      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '创建时间',

    updateTime      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
    COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_eventId (eventId),

    KEY idx_sendStatus_nextRetryTime (sendStatus, nextRetryTime)
    )
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = 'Kafka 消息待发送表';