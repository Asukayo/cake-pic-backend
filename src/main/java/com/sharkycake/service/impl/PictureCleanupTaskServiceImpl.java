package com.sharkycake.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.model.entity.PictureCleanupTask;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.dto.picture.PictureCleanupTaskQueryRequest;
import com.sharkycake.model.vo.PictureCleanupTaskVO;
import com.sharkycake.service.UserService;
import com.sharkycake.model.enums.PictureCleanupResultEnum;
import com.sharkycake.service.PictureCleanupTaskService;
import com.sharkycake.mapper.PictureCleanupTaskMapper;
import com.sharkycake.service.PictureFileCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author shark
* @description 针对表【picture_cleanup_task(图片 COS 文件清理任务)】的数据库操作Service实现
* @createDate 2026-09-15 14:55:36
*/
@Slf4j
@Service
public class PictureCleanupTaskServiceImpl extends
        ServiceImpl<PictureCleanupTaskMapper, PictureCleanupTask>
    implements PictureCleanupTaskService{

    @Resource
    private PictureFileCleanupService pictureFileCleanupService;

    @Resource
    private UserService userService;

    @Override
    public PictureCleanupTaskVO getTaskView(String eventId, User loginUser) {
        checkCurrentAdmin(loginUser);
        ThrowUtils.throwIf(StrUtil.isBlank(eventId) || eventId.length() > 64,
                ErrorCode.PARAMS_ERROR, "eventId不能为空且最长64字符");
        PictureCleanupTask task = getOne(taskViewQuery().eq("eventId", eventId));
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "清理任务不存在");
        return PictureCleanupTaskVO.objToVo(task);
    }

    @Override
    public Page<PictureCleanupTaskVO> listTaskViews(PictureCleanupTaskQueryRequest query, User loginUser) {
        checkCurrentAdmin(loginUser);
        ThrowUtils.throwIf(query == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(query.getCurrent() < 1 || query.getPageSize() < 1 || query.getPageSize() > 100,
                ErrorCode.PARAMS_ERROR, "current必须大于0，pageSize必须为1到100");
        ThrowUtils.throwIf(query.getPictureId() != null && query.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR, "pictureId必须大于0");
        ThrowUtils.throwIf(query.getTaskStatus() != null && (query.getTaskStatus() < 0 || query.getTaskStatus() > 5),
                ErrorCode.PARAMS_ERROR, "taskStatus必须为0到5");
        String eventId = query.getEventId();
        ThrowUtils.throwIf(eventId != null && (StrUtil.isBlank(eventId) || eventId.length() > 64),
                ErrorCode.PARAMS_ERROR, "eventId传入时不能为空且最长64字符");
        QueryWrapper<PictureCleanupTask> wrapper = taskViewQuery()
                .eq(eventId != null, "eventId", eventId)
                .eq(query.getPictureId() != null, "pictureId", query.getPictureId())
                .eq(query.getTaskStatus() != null, "taskStatus", query.getTaskStatus())
                .orderByDesc("createTime", "id");
        Page<PictureCleanupTask> page = page(new Page<>(query.getCurrent(), query.getPageSize()), wrapper);
        Page<PictureCleanupTaskVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(PictureCleanupTaskVO::objToVo).collect(Collectors.toList()));
        return result;
    }

    private QueryWrapper<PictureCleanupTask> taskViewQuery() {
        return new QueryWrapper<PictureCleanupTask>().select(
                "id", "eventId", "pictureId", "operatorId", "taskStatus", "createTime", "updateTime");
    }

    private void checkCurrentAdmin(User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        User currentUser = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(currentUser == null || !userService.isAdmin(currentUser), ErrorCode.NO_AUTH_ERROR);
    }
    // 当前进程中正在执行的任务
    private final Set<String> runningEvents = ConcurrentHashMap.newKeySet();
    private static final long RECOVERY_DELAY_MS = 5 * 60_000L;

    @Override
    public void processTask(String eventId) {
        runExclusive(eventId, () -> executeTask(eventId));
    }

    /**
     * 消费、补偿和人工重试统一经过此方法。
     */
    private void runExclusive(String eventId, Runnable action) {
        ThrowUtils.throwIf(
                StrUtil.isBlank(eventId),
                ErrorCode.PARAMS_ERROR,
                "eventId不能为空"
        );

        if (!runningEvents.add(eventId)) {
            throw new IllegalStateException("任务正在执行，eventId=" + eventId);
        }

        try {
            action.run();
        } finally {
            runningEvents.remove(eventId);
        }
    }

    private void executeTask(String eventId) {
        ThrowUtils.throwIf(StrUtil.isBlank(eventId)
                , ErrorCode.PARAMS_ERROR, "eventId不能为空");
        // 1。根据消息中的eventId查询任务
        PictureCleanupTask task = loadTask(eventId);
        // 2.判断当前状态
        Integer taskStatus = task.getTaskStatus();
        if (taskStatus == null) {
            throw new IllegalStateException("清理任务状态为空");
        }
        // 已完成、等待引用释放、需人工处理：普通重复消息不再执行
        if (taskStatus == 2 || taskStatus == 4 || taskStatus == 5) {
            return;
        }
        ThrowUtils.throwIf(
                taskStatus == 1, ErrorCode.OPERATION_ERROR, "清理任务正在处理中，eventId=" + eventId
        );
        ThrowUtils.throwIf(
                taskStatus != 0 && taskStatus != 3,
                ErrorCode.PARAMS_ERROR, "未知的清理任务状态" + taskStatus
        );
        // 3.原子抢占当前行数据：只有待处理、待重试的任务可以进入处理中
        boolean claimed = lambdaUpdate()
                .eq(PictureCleanupTask::getId, task.getId())
                .in(PictureCleanupTask::getTaskStatus, 0, 3)
                .set(PictureCleanupTask::getTaskStatus, 1)
                .set(PictureCleanupTask::getUpdateTime, new Date())
                .update();
        ThrowUtils.throwIf(!claimed, ErrorCode.OPERATION_ERROR
                , "任务状态已经变化，抢占失败，eventId = " + eventId);
        // 4. 执行COS清理
        PictureCleanupResultEnum result;
        try {
            result = pictureFileCleanupService.cleanup(task);
        } catch (RuntimeException e) {
            log.error("图片文件清理失败，eventId={}", eventId, e);
            updateProcessingStatus(task.getId(), 3);
            throw e;
        }

        // 5. 清理结果落库，放在上面的 catch 范围之外
        updateProcessingStatus(task.getId(), toTaskStatus(result));
    }

    private PictureCleanupTask loadTask(String eventId) {
        PictureCleanupTask task = lambdaQuery()
                .eq(PictureCleanupTask::getEventId, eventId)
                .one();

        ThrowUtils.throwIf(
                task == null,
                ErrorCode.NOT_FOUND_ERROR,
                "清理任务不存在，eventId=" + eventId
        );
        return task;
    }

    /**
     *  检查清理任务是否过期
     */
    private boolean isExpired(PictureCleanupTask task) {
        return task.getUpdateTime() != null
                && task.getUpdateTime().getTime()
                <= System.currentTimeMillis() - RECOVERY_DELAY_MS;
    }

    // 它主要做三件事：
    //1. 筛选候选任务：查询超过 5 分钟没有更新，且处于“待处理”或“处理中”的任务。
    //2. 确认可以恢复：通过 runningEvents 检查当前进程是否正在执行这个任务；取得执行资格后，再查一次数据库，确认状态和时间仍满足条件。
    //3. 重新执行清理：符合条件才恢复任务，进入统一清理流程。
    //关键是：超过五分钟只是进入检查范围，不代表直接认定任务已经卡死。 如果当前进程仍在执行，就不会重置它。
    @Override
    public void recoverStuckTasks() {
        Date cutoff = new Date(
                System.currentTimeMillis() - RECOVERY_DELAY_MS
        );

        List<PictureCleanupTask> candidates = lambdaQuery()
                .in(PictureCleanupTask::getTaskStatus, 0, 1)
                .le(PictureCleanupTask::getUpdateTime, cutoff)
                .orderByAsc(PictureCleanupTask::getUpdateTime)
                .orderByAsc(PictureCleanupTask::getId)
                .last("LIMIT 100")
                .list();

        for (PictureCleanupTask candidate : candidates) {
            String eventId = candidate.getEventId();

            try {
                runExclusive(eventId, () -> {
                    // 取得执行资格后重新查询，不能使用扫描时的旧状态
                    PictureCleanupTask current = loadTask(eventId);
                    Integer status = current.getTaskStatus();

                    if (status == null
                            || (status != 0 && status != 1)
                            || !isExpired(current)) {
                        return;
                    }

                    retryLoadedTask(current);
                });
            } catch (RuntimeException e) {
                log.warn("清理任务补偿未完成，eventId={}", eventId, e);
            }
        }
    }

    /**
     * 调用方必须已经通过 runExclusive 取得执行资格。
     */
    private void retryLoadedTask(PictureCleanupTask task) {
        Integer status = task.getTaskStatus();

        if (status == null || status < 0 || status > 5) {
            throw new IllegalStateException("清理任务状态不合法");
        }

        // 已完成任务不重复执行
        if (status == 2) {
            return;
        }
        // 状态 1 需要同时满足：没有活跃执行者、已超过恢复时间
        if (status == 1 && !isExpired(task)) {
            throw new IllegalStateException("任务尚未达到恢复时间");
        }
        if (status != 0) {
            boolean updated = lambdaUpdate()
                    .eq(PictureCleanupTask::getId, task.getId())
                    .eq(PictureCleanupTask::getTaskStatus, status)
                    .set(PictureCleanupTask::getTaskStatus, 0)
                    .set(PictureCleanupTask::getUpdateTime, new Date())
                    .update();

            ThrowUtils.throwIf(
                    !updated,
                    ErrorCode.OPERATION_ERROR,
                    "恢复任务状态失败"
            );
        }
        // 这里直接调用内部方法，不能再次调用带执行保护的 processTask
        executeTask(task.getEventId());
    }

    @Override
    public void retryTask(String eventId) {
        ThrowUtils.throwIf(StrUtil.isBlank(eventId) || eventId.length() > 64,
                ErrorCode.PARAMS_ERROR, "eventId不能为空且最长64字符");
        runExclusive(eventId, () -> retryLoadedTask(loadTask(eventId)));
    }
    private int toTaskStatus(PictureCleanupResultEnum result) {
        if (result == null) {
            throw new IllegalStateException("清理结果不能为空");
        }
        switch (result) {
            case COMPLETED:
                return 2;
            case WAITING_REFERENCE:
                return 4;
            case MISSING_METADATA:
                return 5;
            default:
                throw new IllegalStateException("未知的清理结果：" + result);
        }
    }
    /**
     * 将“处理中”的任务更新为指定状态
     */
    private void updateProcessingStatus(Long taskId, int targetStatus) {
        boolean updated = lambdaUpdate()
                .eq(PictureCleanupTask::getId, taskId)
                .eq(PictureCleanupTask::getTaskStatus, 1)
                .set(PictureCleanupTask::getTaskStatus, targetStatus)
                .set(PictureCleanupTask::getUpdateTime, new Date())
                .update();
        if (!updated) {
            throw new IllegalStateException("更新清理任务状态失败，taskId=" + taskId);
        }
    }
}




