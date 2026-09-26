package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 通过权限校验后签发的单张私有预览图短时访问地址。 */
@Data
public class ProofingAssetAccessVO implements Serializable {

    private String url;

    private Date expiresAt;
}
