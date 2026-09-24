package com.sharkycake.proofing.dto;

import com.sharkycake.common.PageRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * 按空间分页查询选片单的请求参数。
 */
@Data
public class ProofingProjectQueryRequest extends PageRequest implements Serializable {

    private Long spaceId;


    private String status;

    /**
     * 接收接口文档中的 page 参数，复用 PageRequest 的 current 页码。
     */
    public void setPage(int page) {
        setCurrent(page);
    }
}
