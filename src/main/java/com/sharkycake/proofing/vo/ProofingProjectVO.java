package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 返回给前端的选片单信息。
 */
@Data
public class ProofingProjectVO implements Serializable {


    private String projectId;

    private Long spaceId;

    private String title;

    private Integer selectionLimit;
    /**
     * 该选单状态，默认创建时为草稿
     */
    private String status;

    /**
     * 公开项目定位符，不是访问凭证
     */
    private String publicId;

    /**
     * 业务版本号，实际修改时递增
     */
    private Long version;

}
