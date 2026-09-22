package com.sharkycake.service.impl;


import ch.qos.logback.classic.spi.EventArgUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sharkycake.constant.SpaceUserPermissionConstant;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.manager.auth.SpaceUserAuthManager;
import com.sharkycake.mapper.SpaceMapper;
import com.sharkycake.model.entity.*;
import com.sharkycake.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.Objects;

@Service
@Slf4j
public class PictureDeleteServiceImpl implements PictureDeleteService {

    private final PictureService pictureService;
    private final UserService userService;
    private final SpaceService spaceService;
    private final SpaceUserAuthManager spaceUserAuthManager;
    private final PictureCleanupTaskService pictureCleanupTaskService;
    private final MessageOutboxService messageOutboxService;
    private SpaceMapper spaceMapper;

    public PictureDeleteServiceImpl(
            PictureService pictureService,
            UserService userService,
            SpaceService spaceService,
            SpaceMapper spaceMapper,
            SpaceUserAuthManager spaceUserAuthManager,
            PictureCleanupTaskService pictureCleanupTaskService,
            MessageOutboxService messageOutboxService) {

        this.pictureService = pictureService;
        this.userService = userService;
        this.spaceService = spaceService;
        this.spaceMapper = spaceMapper;
        this.spaceUserAuthManager = spaceUserAuthManager;
        this.pictureCleanupTaskService = pictureCleanupTaskService;
        this.messageOutboxService = messageOutboxService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePicture(Long pictureId, Long operatorId) {
        // 1. 删除权限校验
        Picture picture = validateDeletePermission(pictureId, operatorId);
        // 2.逻辑删除图片,并原子的扣减空间额度
        logicalDelete(picture);
        // 4. 保存清理任务和OutBox
        // 4.1 生成唯一的eventId
        String eventId = UUID.randomUUID().toString();
        // 4.2 保存 pictureId，operatorId，bucket和三个key的快照
        saveCleanupTask(picture, operatorId, eventId);
        saveOutbox(eventId,"picture.cleanup.requested");
    }

    /**
     * 后续其他方法可能也会写入outBox，所以单独标记事务，如果有事务就加入，没事务就创建
     */
    @Transactional(rollbackFor = Exception.class,propagation = Propagation.REQUIRED)
    public void saveOutbox(String eventId,String topicId) {
        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(eventId);
        outbox.setTopic(topicId);
        // 生成 JSON，例如：{"eventId":"某个UUID"}
        String payload = JSONUtil.toJsonStr(
                Collections.singletonMap("eventId", eventId)
        );
        outbox.setPayload(payload);
        outbox.setSendStatus(0); // 待发送
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(new Date()); // 创建后即可发送
        boolean saved = messageOutboxService.save(outbox);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "保存清理事件失败");
    }

    /**
     *  创建图片删除任务
     */
    private void saveCleanupTask(Picture picture, Long operatorId, String eventId) {
        PictureCleanupTask task = new PictureCleanupTask();
        task.setEventId(eventId);
        task.setPictureId(picture.getId());
        task.setOperatorId(operatorId);
        // 保存删除时的对象信息，供消费者后续使用
        task.setStorageBucket(picture.getStorageBucket());
        task.setOriginalKey(picture.getOriginalKey());
        task.setCompressedKey(picture.getCompressedKey());
        task.setThumbnailKey(picture.getThumbnailKey());
        task.setTaskStatus(0); // 待处理
        boolean saved = pictureCleanupTaskService.save(task);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "保存图片清理任务失败");
    }

    /**
     * 进行逻辑删除，删除完之后扣减对应空间限额
     */
    private void logicalDelete(Picture picture) {
        ThrowUtils.throwIf(picture == null || picture.getPicSize() == null || picture.getPicSize() < 0,ErrorCode.PARAMS_ERROR);
        // 获取图片元数据
        if (picture.getSpaceId() != null) {
            // 如果是私人空间或者是团队空间，需要进行额度扣减操作
            Space space = spaceService.getById(picture.getSpaceId());
            ThrowUtils.throwIf(space == null,ErrorCode.PARAMS_ERROR,"空间不存在");
            // 3. 原子扣减空间额度
            int affectedRows = spaceMapper.decreaseUsage(
                    picture.getSpaceId(),
                    picture.getPicSize()
            );
            ThrowUtils.throwIf(affectedRows!=1,ErrorCode.OPERATION_ERROR,"扣减空间额度失败");
        }
        // 删除图片
        boolean removed = pictureService.removeById(picture.getId());
        ThrowUtils.throwIf(!removed,ErrorCode.OPERATION_ERROR,"图片删除失败");
    }

    /**
     * 校验用户是否有删除权限
     * 返回的图片是可以删除的图片
     */
    private Picture validateDeletePermission(Long pictureId, Long operatorId) {
        // 1. 校验参数
        ThrowUtils.throwIf(
                pictureId == null || pictureId <= 0
                        || operatorId == null || operatorId <= 0,
                ErrorCode.PARAMS_ERROR,
                "图片 ID 或操作用户 ID 不合法"
        );
        // 2. 查询最新用户身份，避免使用会话里过期的角色信息
        User operator = userService.getById(operatorId);
        ThrowUtils.throwIf(
                operator == null,
                ErrorCode.NOT_LOGIN_ERROR,
                "操作用户不存在或已失效");
        // 3.按照图片Id查询，并在当前事务内锁定这张图片
        Picture picture = pictureService.lambdaQuery()
                .eq(Picture::getId, pictureId).last("For Update").one();
        ThrowUtils.throwIf(picture == null,ErrorCode.NOT_FOUND_ERROR,"该图片不存在");
        // 正式进行权限校验
        // 4. 公共图库，只有上传者和平台管理员可以删除
        if(picture.getSpaceId() == null){
            boolean isAllowed = Objects.equals(picture.getUserId(), operator.getId()) || userService.isAdmin(operator);
            ThrowUtils.throwIf(!isAllowed, ErrorCode.NO_AUTH_ERROR,"只能删除自己的上传的图片");
            return picture;
        }
        // 5.查看用户所属的空间
        Space space = spaceService.getById(picture.getSpaceId());
        ThrowUtils.throwIf(space == null,ErrorCode.NOT_FOUND_ERROR,"该空间不存在");
        // 查看在这个空间中是否有删除图片权限
        boolean isAllowedDeleteInSpace = spaceUserAuthManager.getPermissionList(space, operator)
                .contains(SpaceUserPermissionConstant.PICTURE_DELETE);
        ThrowUtils.throwIf(!isAllowedDeleteInSpace,ErrorCode.NO_AUTH_ERROR,"没有该空间删除权限");

        return picture;
    }
}
