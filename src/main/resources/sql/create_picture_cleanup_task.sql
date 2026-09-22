CREATE TABLE IF NOT EXISTS picture_cleanup_task
(
    id              BIGINT AUTO_INCREMENT
    COMMENT '任务主键',

    eventId         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '任务事件唯一标识，由应用生成，重试时保持不变',

    pictureId       BIGINT NOT NULL
    COMMENT '关联图片 ID，图片记录删除后仍保留',

    operatorId      BIGINT NULL
    COMMENT '操作用户 ID，历史补偿无法确定操作人时为空',

    storageBucket   VARCHAR(128) NULL
    COMMENT 'COS 存储桶，历史数据缺失时需补齐',

    originalKey     VARCHAR(1024) COLLATE utf8mb4_bin NULL
    COMMENT '原图对象 key，历史数据缺失时需补齐',

    compressedKey   VARCHAR(1024) COLLATE utf8mb4_bin NULL
    COMMENT '压缩图对象 key，无压缩图时为空',

    thumbnailKey    VARCHAR(1024) COLLATE utf8mb4_bin NULL
    COMMENT '缩略图对象 key，可能与压缩图相同',

    taskStatus      TINYINT NOT NULL DEFAULT 0
    COMMENT '0-待处理 1-处理中 2-已完成 3-待重试 4-等待引用释放 5-需人工处理',

    createTime      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '创建时间',

    updateTime      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
    COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_eventId (eventId),

    KEY idx_taskStatus_createTime (taskStatus, createTime),

    KEY idx_operatorId (operatorId)
    )
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '图片 COS 文件清理任务';