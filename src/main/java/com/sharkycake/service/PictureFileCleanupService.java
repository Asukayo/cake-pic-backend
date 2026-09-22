package com.sharkycake.service;

import com.sharkycake.model.entity.PictureCleanupTask;
import com.sharkycake.model.enums.PictureCleanupResultEnum;

public interface PictureFileCleanupService {

    PictureCleanupResultEnum cleanup(PictureCleanupTask task);
}
