package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;

/** 客户可见的选片单信息，不包含空间和员工字段。 */
@Data
public class ProofingPublicProjectVO implements Serializable {

    private String projectId;

    private String title;

    private String status;

    private Integer selectionLimit;

    private Long selectedCount;

    private Long version;
}
