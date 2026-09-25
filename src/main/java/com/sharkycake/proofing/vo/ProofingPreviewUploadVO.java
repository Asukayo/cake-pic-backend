package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 单张预览上传结果。只返回页面需要的标识和版本，不暴露私有桶或对象 Key。
 */
@Data
public class ProofingPreviewUploadVO implements Serializable {

    private String itemId;

    private String previewAssetId;

    private String displayName;

    private Integer sortOrder;

    private Integer width;

    private Integer height;

    /** 下一张图片上传时使用这个版本作为 expectedVersion。 */
    private Long version;
}
