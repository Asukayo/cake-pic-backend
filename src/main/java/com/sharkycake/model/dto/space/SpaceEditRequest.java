package com.sharkycake.model.dto.space;


import lombok.Data;

import java.io.Serializable;


/**
 * 空间修改请求
 * 目前仅供用户使用，只允许编辑空间名称
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

}
