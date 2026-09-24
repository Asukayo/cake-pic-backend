package com.sharkycake.picture.upload;

import cn.hutool.core.io.FileUtil;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 本地上传文件子类
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate{
    @Override
    protected void validPicture(Object inputSource) {
        // 由于是用户本地上传的文件，所以直接转为multipartFile即可
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR,"文件不能为空");
        // 1.校验文件大小
        long size = multipartFile.getSize();
        final long TWENTY_MB = 1024 * 20 * 1024L;
        ThrowUtils.throwIf(size > TWENTY_MB,ErrorCode.PARAMS_ERROR,"文件大小不能超过20MB");
        // 2.校验文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMATS = Arrays.asList("jpg", "jpeg", "png", "gif", "bmp");
        ThrowUtils.throwIf(!ALLOW_FORMATS.contains(suffix.toLowerCase()),ErrorCode.PARAMS_ERROR,"不支持该文件类型");
    }

    @Override
    protected String getOriginalFileName(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}
