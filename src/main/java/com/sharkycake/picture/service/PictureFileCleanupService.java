package com.sharkycake.picture.service;

import com.sharkycake.picture.entity.PictureCleanupTask;
import com.sharkycake.picture.enums.PictureCleanupResultEnum;

public interface PictureFileCleanupService {

    PictureCleanupResultEnum cleanup(PictureCleanupTask task);
}
