package com.sharkycake.Schedule;

import com.sharkycake.service.PictureCleanupTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class PictureCleanupRecoveryJob {

    @Resource
    private PictureCleanupTaskService pictureCleanupTaskService;

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        pictureCleanupTaskService.recoverStuckTasks();
    }
}