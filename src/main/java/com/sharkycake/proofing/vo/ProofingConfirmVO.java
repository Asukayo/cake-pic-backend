package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 一次确认的固定结果；完整照片清单由 submission 读取接口提供。 */
@Data
public class ProofingConfirmVO implements Serializable {

    private String submissionId;

    private Integer selectedCount;

    private Date confirmedAt;
}
