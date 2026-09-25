package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 员工预览列表中的单张图片；文件访问链接由单独的授权接口签发。
 */
@Data
public class ProofingItemVO implements Serializable {

    private String itemId;

    private String previewAssetId;

    private String displayName;

    private Integer sortOrder;
}
