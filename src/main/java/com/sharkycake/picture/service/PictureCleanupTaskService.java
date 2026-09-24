package com.sharkycake.picture.service;

import com.sharkycake.picture.entity.PictureCleanupTask;
import com.sharkycake.user.entity.User;
import com.sharkycake.picture.dto.PictureCleanupTaskQueryRequest;
import com.sharkycake.picture.vo.PictureCleanupTaskVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author shark
* @description 针对表【picture_cleanup_task(图片 COS 文件清理任务)】的数据库操作Service
* @createDate 2026-09-15 14:55:36
*/
public interface PictureCleanupTaskService extends IService<PictureCleanupTask> {

    /** 根据事件 ID 查询管理视图，使用数据库中的最新平台管理员身份鉴权。 */
    PictureCleanupTaskVO getTaskView(String eventId, User loginUser);

    /** 分页查询管理视图；只读，不执行重试或 COS 清理。 */
    Page<PictureCleanupTaskVO> listTaskViews(PictureCleanupTaskQueryRequest query, User loginUser);

    void processTask(String eventId);

    void recoverStuckTasks();

    void retryTask(String eventId);
}
