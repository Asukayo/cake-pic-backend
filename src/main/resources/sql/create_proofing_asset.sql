CREATE TABLE proofing_asset (
                                id BIGINT NOT NULL AUTO_INCREMENT,
                                projectId BIGINT NOT NULL COMMENT '所属选单项目 ID',
                                uploadedBy BIGINT NOT NULL COMMENT '上传员工 ID；ZIP 记录任务发起者 ID',
                                kind VARCHAR(16) NOT NULL COMMENT 'PREVIEW、FINAL、ZIP',
                                bucket VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                objectKey VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                sizeBytes BIGINT NOT NULL COMMENT '实际文件字节数',
                                sha256 CHAR(64) NULL COMMENT '文件内容 SHA-256 摘要',
                                width INT NULL COMMENT '图片宽度；ZIP 为空',
                                height INT NULL COMMENT '图片高度；ZIP 为空',
                                contentType VARCHAR(64) NOT NULL COMMENT '实际存储文件的媒体类型',
                                status VARCHAR(24) NOT NULL DEFAULT 'STAGING'
                                    COMMENT 'STAGING、READY、DELETE_PENDING',
                                expiresAt DATETIME(3) NULL COMMENT 'STAGING 清理时间',
                                createTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                updateTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_bucket_objectKey (bucket, objectKey),
                                KEY idx_project_status (projectId, status),
                                KEY idx_status_expiresAt (status, expiresAt)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;