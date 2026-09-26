package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;

/** 一次确认生成一个 UUID；响应丢失后的重试沿用相同 requestId。 */
@Data
public class ProofingConfirmRequest implements Serializable {

    private String requestId;

    private Long expectedVersion;
}
