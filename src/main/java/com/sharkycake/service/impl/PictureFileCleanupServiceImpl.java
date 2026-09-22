package com.sharkycake.service.impl;


import cn.hutool.core.util.StrUtil;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.manager.CosManager;
import com.sharkycake.model.entity.Picture;
import com.sharkycake.model.entity.PictureCleanupTask;
import com.sharkycake.model.enums.PictureCleanupResultEnum;
import com.sharkycake.service.PictureFileCleanupService;
import com.sharkycake.service.PictureService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class PictureFileCleanupServiceImpl implements PictureFileCleanupService {


    private final PictureService pictureService;
    private final CosManager cosManager;

    public PictureFileCleanupServiceImpl(
            PictureService pictureService,
            CosManager cosManager) {
        this.pictureService = pictureService;
        this.cosManager = cosManager;
    }


    @Override
    public PictureCleanupResultEnum cleanup(PictureCleanupTask task) {
        ThrowUtils.throwIf(task == null, ErrorCode.PARAMS_ERROR,"清理任务不能为空");
        // 1. 检查必要的对象信息
        if (!hasRequiredMetadata(task)) {
            return PictureCleanupResultEnum.MISSING_METADATA;
        }
        // 2. 收集需要清理的 key
        String bucket = task.getStorageBucket();
        Set<String> keys = collectKeys(task);
        // 2. 先检查全部 key，仍有引用时暂时保留整组文件
        for (String key : keys) {
            if (hasActiveReference(bucket, key)) {
                return PictureCleanupResultEnum.WAITING_REFERENCE;
            }
        }
        // 3. 没有有效引用，逐个删除
        // 任何一次调用失败都会抛异常，不会执行到下面的 COMPLETED
        for (String key : keys) {
            cosManager.deleteObject(bucket, key);
        }
        // 4. 所有删除调用成功后，才返回完成
        return PictureCleanupResultEnum.COMPLETED;
    }

    // 检查是否有必要的元数据
    private boolean hasRequiredMetadata(PictureCleanupTask task) {
        return StrUtil.isNotBlank(task.getStorageBucket())
                && StrUtil.isNotBlank(task.getOriginalKey());
    }
    // 收集需要删除的key
    private Set<String> collectKeys(PictureCleanupTask task) {
        Set<String> keys = new LinkedHashSet<>(Arrays.asList(
                task.getOriginalKey(),
                task.getCompressedKey(),
                task.getThumbnailKey()
        ));

        keys.removeIf(StrUtil::isBlank);
        return keys;
    }
    // 检查图片有没有额外的引用
    // WHERE isDelete = 0
    //  AND storageBucket = 指定桶
    //  AND (
    //      originalKey = 指定key
    //      OR compressedKey = 指定key
    //      OR thumbnailKey = 指定key
    //  )
    private boolean hasActiveReference(String bucket, String key) {
        return pictureService.lambdaQuery()
                .eq(Picture::getStorageBucket, bucket)
                .and(q -> q.eq(Picture::getOriginalKey, key)
                        .or()
                        .eq(Picture::getCompressedKey, key)
                        .or()
                        .eq(Picture::getThumbnailKey, key))
                .exists();
    }
}
