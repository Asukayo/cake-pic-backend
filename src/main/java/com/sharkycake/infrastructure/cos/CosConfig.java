package com.sharkycake.infrastructure.cos;


import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosConfig {

    /**
     * 域名
     */
    private String host;

    /**
     * secretId
     */
    private String secretId;

    /**
     * 密钥（注意不要泄露）
     */
    private String secretKey;

    /**
     * 区域
     */
    private String region;

    /**
     * 桶名
     */
    private String bucket;

    /**
     * 私有存储桶名称
     */
    private String proofingBucket;

    @Bean
    public COSClient CosClient() {
        // 初始化用户身份信息（secretId,secretKey）
        BasicCOSCredentials credentials = new BasicCOSCredentials(this.secretId, this.secretKey);
        // 设置bucket的区域，COS地域简称请参照
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 生成cos客户端
        return new COSClient(credentials, clientConfig);
    }

}
