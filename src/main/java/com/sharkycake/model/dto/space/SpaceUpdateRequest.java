package com.sharkycake.model.dto.space;


import lombok.Data;

import java.io.Serializable;


/**
 * 仅供管理员的修改请求
 */
@Data
public class SpaceUpdateRequest implements Serializable {

    /**
     * 空间id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间等级： 0-普通版，1-专业版，2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;
}
