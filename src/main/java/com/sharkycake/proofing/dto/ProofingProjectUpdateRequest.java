package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改草稿选片单的请求参数。
 */
@Data
public class ProofingProjectUpdateRequest implements Serializable {

    private String title;

    private Integer selectionLimit;

    private Long expectedVersion;
}
