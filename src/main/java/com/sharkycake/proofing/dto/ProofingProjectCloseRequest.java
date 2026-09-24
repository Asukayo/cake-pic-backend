package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 根据预期的版本号关闭对应选单
 */
@Data
public class ProofingProjectCloseRequest implements Serializable {

    private Long expectedVersion;
}
