package com.sharkycake.proofing.vo;


import lombok.Data;

import java.io.Serializable;

/**
 * 返回图片选择结果
 */
@Data
public class ProofingSelectVO implements Serializable {

    public Boolean operateSuccess;

    public Boolean selected;

    public Long selectedCount;

    public Long currentVersion;

    public Integer limitationLeft;

}
