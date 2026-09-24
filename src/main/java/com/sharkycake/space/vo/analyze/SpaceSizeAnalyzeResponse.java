package com.sharkycake.space.vo.analyze;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * 视图响应类，分析结果需要返回图片大小范围和对应的图片数量
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceSizeAnalyzeResponse implements Serializable {

    /**
     * 图片大小范围
     */
    private String sizeRange;

    /**
     * 图片数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}

