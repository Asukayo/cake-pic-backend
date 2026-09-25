package com.sharkycake.infrastructure.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.Date;

/**
 * 私有存储桶管理器
 */
@Component
public class ProofingStorageManager {

    private final CosConfig cosConfig;

    private final COSClient cosClient;

    public ProofingStorageManager(CosConfig cosConfig) {
        this.cosConfig = cosConfig;
        // 与原图库共用凭证，但为 proofing 单独限定一次 COS 请求的最长时间。
        ClientConfig clientConfig = new ClientConfig(new Region(cosConfig.getRegion()));
        clientConfig.setConnectionTimeout(10_000);
        clientConfig.setSocketTimeout(60_000);
        clientConfig.setRequestTimeout(5 * 60_000);
        clientConfig.setRequestTimeOutEnable(true);
        this.cosClient = new COSClient(
                new BasicCOSCredentials(cosConfig.getSecretId(), cosConfig.getSecretKey()), clientConfig);
    }

    /** 显式设置媒体类型，签名访问预览时浏览器才能按图片展示。 */
    public PutObjectResult putObject(String key, File file, String contentType) {
        PutObjectRequest request = new PutObjectRequest(cosConfig.getProofingBucket(), key, file);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        request.setMetadata(metadata);
        return cosClient.putObject(request);
    }

    public void delete(String key){
        cosClient.deleteObject(cosConfig.getProofingBucket(), key);
    }

    /** 清理时使用资产记录的原始桶名，避免配置换桶后遗漏旧对象。 */
    public void delete(String bucket, String key) {
        cosClient.deleteObject(bucket, key);
    }

    /** 使用当前私有桶生成 GET 预签名 URL。 */
    public String signGet(String key, int ttlSeconds) {
        return signGet(cosConfig.getProofingBucket(), key, ttlSeconds);
    }

    /** 使用资产记录中的桶名签名，避免换桶后把旧资产签到新桶。 */
    public String signGet(String bucket, String key, int ttlSeconds) {
        ThrowUtils.throwIf(ttlSeconds < 1 || ttlSeconds > 120,
                ErrorCode.PARAMS_ERROR, "有效期必须为 1～120 秒");

        Date expiration = new Date(System.currentTimeMillis() + ttlSeconds * 1000L);
        return cosClient.generatePresignedUrl(
                bucket, key, expiration, HttpMethodName.GET
        ).toExternalForm();
    }

    @PreDestroy
    public void shutdown() {
        cosClient.shutdown();
    }

}
