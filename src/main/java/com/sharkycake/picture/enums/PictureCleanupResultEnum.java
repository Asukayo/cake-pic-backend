package com.sharkycake.picture.enums;

public enum PictureCleanupResultEnum {

    // 文件已全部清理完成
    COMPLETED,

    // 仍有其他有效图片引用，暂时不能全部清理
    WAITING_REFERENCE,

    // 缺少 bucket 或原图 key，需要补齐信息
    MISSING_METADATA
}