package com.sharkycake.picture.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.sharkycake.infrastructure.cos.CosConfig;
import com.sharkycake.infrastructure.cos.CosManager;
import com.sharkycake.picture.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosConfig cosConfig;

    /**
     * 模板方法，定义上传流程
     * 从处理结果中获取到缩略图，并设置到返回结果中
     * 将缩略图路径也设置到返回结果中
     */
    public final UploadPictureResult uploadPicture(Object inputSource,String uploadPathPrefix) {
        // 1.校验图片
        validPicture(inputSource);
        // 2.获取图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginalFileName(inputSource);
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),
                uuid, FileUtil.getSuffix(originFilename));

        // aliyunai扩图上传，会增加？后面的后缀，导致无法上传
        if(uploadFileName.contains("?")){
            uploadFileName = uploadFileName.split("\\?")[0];
        }
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        File uploadFile = null;
        try{
            // 3. 创建临时文件
            uploadFile = File.createTempFile(uploadPath,null);
            //  处理文件来源（本地或者URL）
            processFile(inputSource,uploadFile);
            // 4. 上传图片到对象存储服务
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, uploadFile);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 从处理结果中获取到缩略图，并设置到返回结果中
            // 获取 COS 返回的图片处理结果
            ProcessResults processResults =
                    putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults == null
                    ? null
                    : processResults.getObjectList();
// 先声明结果，两个分支分别构造它
            UploadPictureResult result;
            if (CollUtil.isNotEmpty(objectList)) {
                // 第一项：压缩图
                CIObject compressedCiObject = objectList.get(0);
                // 没有单独生成缩略图时，使用压缩图作为缩略图
                CIObject thumbnailCiObject = compressedCiObject;
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                // 保留原有的图片名称、尺寸、URL 等封装逻辑
                result = buildResult(
                        originFilename,
                        compressedCiObject,
                        thumbnailCiObject,
                        imageInfo);
                // 新增：记录 COS 返回的真实对象 Key
                result.setCompressedKey(compressedCiObject.getKey());
                result.setThumbnailKey(thumbnailCiObject.getKey());
            } else {
                // 没有派生图片时，只使用原图
                result = buildResult(
                        originFilename,
                        uploadFile,
                        uploadPath,
                        imageInfo);
            }
// 两个分支都需要保存原图 Key 和存储桶
            result.setOriginalKey(uploadPath);
            result.setStorageBucket(cosConfig.getBucket());

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            // 6.清理临时文件
            deleteTempFile(uploadFile);
        }
    }

    /**
     * 校验输入源（本地文件或URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 根据输入源获取原始的文件名
     * @param inputSource 输入源，本地文件或为URL
     * @return  文件名称
     */
    protected abstract String getOriginalFileName(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     * @param inputSource   输入源，本地文件或者为URL
     * @param file  用于存储本地临时文件
     * @throws Exception
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 用于封装Upload返回结果
     * @param originFilename 文件名
     * @param file  创建的临时文件，用于存储本地上传的图片
     * @param uploadPath    图片上传的路径
     * @param imageInfo     图片信息元数据
     * @return
     */
    private UploadPictureResult buildResult(String originFilename,
                                            File file, String uploadPath,
                                            ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight ,2).doubleValue();

        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setUrl(cosConfig.getHost() + uploadPath);
        // 新增存储颜色功能
        uploadPictureResult.setPicColor(imageInfo.getAve());



        return uploadPictureResult;
    }

    /**
     * 编写重载方法
     * 从压缩图片中获取图片信息
     * 将缩略图url赋值到UploadPictureResult中
     * @param originFilename     图片名称
     * @param compressedCiObject 压缩结果
     * @param thumbnailCiObject  缩略图结果
     * @return
     */
    private UploadPictureResult buildResult(
            String originFilename,
            CIObject compressedCiObject,
            CIObject thumbnailCiObject,
            ImageInfo imageInfo) {
        // 构建返回结果对象
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        //
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight ,2).doubleValue();

        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        // 设置图片url为压缩之后的webp文件的地址
        uploadPictureResult.setUrl(cosConfig.getHost() + "/" + compressedCiObject.getKey());
        // 设置缩略图url
        uploadPictureResult.setThumbnailUrl(cosConfig.getHost() + "/" + thumbnailCiObject.getKey());

        return uploadPictureResult;
    }



    /**
     * 用于删除临时文件
     * @param file 临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        boolean delete = file.delete();
        if (!delete){
            log.error("file delete error,filePath:{}",file.getAbsolutePath());
        }
    }



}
