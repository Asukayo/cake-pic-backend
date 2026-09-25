package com.sharkycake.proofing.vo;


import lombok.Data;

import java.util.Date;

/**
 * 用于存储创建临时连接结构体
 */
@Data
public class ProofingSharedVO {

    /**
     * 由谁创建
     */
    public Long createdBy;

    /**
     * 对应哪个项目id
     */
    public String projectId;

    /**
     * 临时分享链接
     */
    public String shareUrl;

    /**
     * 连接过期时间
     */
    public Date expireDate;

}
