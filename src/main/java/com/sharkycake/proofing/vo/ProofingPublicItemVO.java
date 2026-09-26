package com.sharkycake.proofing.vo;

import com.sharkycake.proofing.entity.ProofingItem;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/** 客户可见的预览明细；当前页直接带短时预览地址。 */
@Data
public class ProofingPublicItemVO implements Serializable {

    private String itemId;

    private String previewAssetId;

    private String displayName;

    private Integer sortOrder;

    private Boolean selected;

    private String previewUrl;

    private Date previewUrlExpiresAt;


}
