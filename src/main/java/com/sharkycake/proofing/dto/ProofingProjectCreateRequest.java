package com.sharkycake.proofing.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建选片单的请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProofingProjectCreateRequest implements Serializable {

    /**
     * 所属空间 ID
     */
    private Long spaceId;

    /**
     * 选单标题
     */
    private String title;

    /**
     * 可选择图片数量限制
     */
    private Integer selectionLimit;


}
