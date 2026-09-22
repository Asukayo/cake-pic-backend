package com.sharkycake.manager.upload;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import javax.lang.model.type.ArrayType;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * 用于来自url的图片上传操作
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate{

    @Override
    protected void validPicture(Object inputSource) {
        String url = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.PARAMS_ERROR,"文件地址不能为空");
        // 判断协议
        ThrowUtils.throwIf(!(url.startsWith("http://") || url.startsWith("https://"))
                ,ErrorCode.PARAMS_ERROR,"url协议不合规");
        // 1.创建Http请求并发送
        HttpResponse response = null;
        try{
            new URL(url);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件地址格式不合法");
        }
        try{
            
            response = HttpUtil.createRequest(Method.HEAD,url).execute();
            // 请求不成功，直接返回，不进行校验操作
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 3. 校验文件格式是否合法
            String contentType = response.header("Content-Type");
            final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/png","image/webp");
            ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),ErrorCode.PARAMS_ERROR,"文件类型错误");
            // 2.校验文件大小
            String size = response.header("Content-Length");
            long picSize = Long.parseLong(size);
            final long TWENTY_MB = 1024 * 1024 * 20L;
            ThrowUtils.throwIf(picSize > TWENTY_MB,ErrorCode.PARAMS_ERROR,"文件大小不能大于20MB");
        } finally {
            // 最后释放请求
            if (response != null){
                response.close();
            }
        }
    }

    @Override
    protected String getOriginalFileName(Object inputSource) {
        String url = (String) inputSource;

        return FileUtil.getName(url);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String url = (String) inputSource;
        HttpUtil.downloadFile(url, file);

    }
}
