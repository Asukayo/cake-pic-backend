package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;

/** 发布选单时提交的项目版本。 */
@Data
public class ProofingProjectPublishRequest implements Serializable {

    private Long expectedVersion;
}
