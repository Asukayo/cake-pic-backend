package com.sharkycake.infrastructure.cos;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.sharkycake.infrastructure.cos.CosConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * 提供通用的对象存储操作，比如文件上传，文件下载等
 * Manager是人为约定的一种写法，表示通用的，可复用的能力，可供其他代码调用
 */
@Component
public class CosManager {

    @Resource
    private CosConfig cosConfig;

    @Resource
    private COSClient cosClient;

    // ...一些操作COS的方法

    /**
     * 上传对象
     * @param key 唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest =
                new PutObjectRequest(cosConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     * @param key 唯一键
     * @return
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 删除对象
     * @param key   文件key
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosConfig.getBucket(), key);
    }


    public void deleteObject(String bucket, String key) {
        cosClient.deleteObject(bucket, key);
    }
    /**
     * 上传对象（附带图片信息）
     * 对图片进行处理，将图片后缀转为webp，并使用数据万象将图片格式转为webp
     * 使用数据万象为图片创建缩略图，设置最大宽高，进行等比缩小
     * @param key 唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucket(), key, file);
        // 对图片进行处理（获取基本信息也是一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 图片压缩（转成webp格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosConfig.getBucket());
        compressRule.setFileId(webpKey);
        rules.add(compressRule);
        // 缩略图处理,只对> 1MB的图片生成缩略图
        if (file.length() > 1024 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setBucket(cosConfig.getBucket());
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>>(如果大于原图宽高，则不处理)
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>",512,512));
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }




}
