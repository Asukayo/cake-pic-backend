CREATE TABLE proofing_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    projectId BIGINT NOT NULL COMMENT '已确认选单 ID；每单只确认一次',
    requestId VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '一次确认的客户端 UUID；重试沿用原值',
    manifestJson JSON NOT NULL COMMENT '按展示顺序固定的已选照片及批注',
    confirmedAt DATETIME(3) NOT NULL COMMENT '服务端确认时间，按 UTC 保存',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project (projectId)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
