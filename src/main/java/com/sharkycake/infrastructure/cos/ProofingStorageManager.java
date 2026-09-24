package com.sharkycake.infrastructure.cos;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 私有存储桶管理器
 */
@Component
public class ProofingStorageManager {

    private CosConfig cosConfig;

    private COSClient cosClient;

    public ProofingStorageManager(CosConfig cosConfig,COSClient cosClient) {
        this.cosConfig = cosConfig;
        this.cosClient = cosClient;
    }

    public PutObjectResult putObject(String key, File file) {
        return cosClient.putObject(cosConfig.getProofingBucket(), key, file);
    }

    public void delete(String key){
        // deleteObject(proofingBucket, key)
        cosClient.deleteObject(cosConfig.getProofingBucket(), key);

    }

    /**
     * // 生成 GET 预签名 URL，限制 1～120 秒
     */
    public String signGet(String key, int ttlSeconds) {
        ThrowUtils.throwIf(ttlSeconds < 1 || ttlSeconds > 120,
                ErrorCode.PARAMS_ERROR, "有效期必须为 1～120 秒");

        Date expiration = new Date(System.currentTimeMillis() + ttlSeconds * 1000L);
        return cosClient.generatePresignedUrl(
                cosConfig.getProofingBucket(), key, expiration, HttpMethodName.GET
        ).toExternalForm();
    }

}
