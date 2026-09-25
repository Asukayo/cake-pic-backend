package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 员工查看私有预览图时使用的短时访问地址。 */
@Data
public class ProofingAssetAccessVO implements Serializable {

    private String url;

    private Date expiresAt;
}
