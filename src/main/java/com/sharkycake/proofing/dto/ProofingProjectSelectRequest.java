package com.sharkycake.proofing.dto;


import lombok.Data;

import java.io.Serializable;


/**
 * 选择/取消图片时使用的请求
 */
@Data
public class ProofingProjectSelectRequest implements Serializable {

    private Boolean selected;

    private Long expectedVersion;
}
