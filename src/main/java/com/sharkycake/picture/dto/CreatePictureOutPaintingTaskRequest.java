package com.sharkycake.picture.dto;

import com.sharkycake.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;


/**
 * AI扩图请求类
 * 用于结构前端传来的参数并传递给Service服务层
 */

@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;

    private static final long serialVersionUID = 1L;
}
