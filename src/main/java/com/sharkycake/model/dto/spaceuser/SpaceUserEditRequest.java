package com.sharkycake.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;


/**
 * 编辑空间成员请求，供空间管理员使用，可以设置空间成员的角色
 */
@Data
public class SpaceUserEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}

