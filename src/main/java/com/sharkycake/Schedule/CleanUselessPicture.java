package com.sharkycake.Schedule;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sharkycake.mapper.PictureMapper;
import com.sharkycake.model.entity.Picture;
import com.sharkycake.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 每日半夜定时删除已经被标记为删除的图片
 */
@Component
@Slf4j
public class CleanUselessPicture {

    @Resource
    private PictureMapper pictureMapper;
    @Resource
    private PictureService pictureService;

    //每天执行,清理COS空间
//    @Scheduled(cron = "0 20 3 * * *")
    public void cleanUselessPicture() {
        long afterId = 0L;

        while (true) {
            List<Picture> pictures =
                    pictureMapper.selectDeletedAfterId(afterId, 100);

            if (pictures.isEmpty()) {
                return;
            }

            for (Picture picture : pictures) {
                // 即使这张失败，也继续处理后面的记录
                afterId = picture.getId();

                try {
                    pictureService.clearPictureFile(picture);
                } catch (Exception e) {
                    log.error(
                            "COS 清理失败，等待下次重试，pictureId={}",
                            picture.getId(), e);
                }
            }
        }
    }


}
