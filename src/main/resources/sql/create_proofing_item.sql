CREATE TABLE proofing_item (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               projectId BIGINT NOT NULL COMMENT '所属选单项目 ID',
                               previewAssetId BIGINT NOT NULL COMMENT '预览文件 ID',
                               displayName VARCHAR(128) NOT NULL COMMENT '展示名称',
                               sortOrder INT NOT NULL COMMENT '展示顺序',
                               selected TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否选中',
                               annotation JSON NULL COMMENT '文字及可选矩形批注',
                               finalAssetId BIGINT NULL COMMENT '成片文件 ID',
                               createTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                               updateTime DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                   ON UPDATE CURRENT_TIMESTAMP(3),
                               PRIMARY KEY (id),
                               UNIQUE KEY uk_project_previewAsset (projectId, previewAssetId),
                               KEY idx_project_selected_sort (projectId, selected, sortOrder, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;