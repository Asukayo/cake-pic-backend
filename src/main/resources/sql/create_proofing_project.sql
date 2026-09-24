CREATE TABLE proofing_project (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  spaceId BIGINT NOT NULL COMMENT '该选单表存在团队空间中',
                                  createdBy BIGINT NOT NULL COMMENT '创建用户id',
                                  title VARCHAR(128) NOT NULL COMMENT '选单标题',
                                  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT '该选单状态，默认创建时为草稿',
                                  selectionLimit INT NOT NULL COMMENT '可选择图片数量限制',
                                  publicId VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                                      COMMENT '公开项目定位符，不是访问凭证',
                                  version BIGINT NOT NULL DEFAULT 0 COMMENT '业务版本号，实际修改时递增',
                                  createTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  updateTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_publicId (publicId),
                                  KEY idx_space_status_createTime (spaceId, status, createTime)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;