package com.sharkycake.proofing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 选片单项目，对应 proofing_project 表。
 */
@TableName(value ="proofing_project")
@Data
public class ProofingProject implements Serializable {
    /**
     *
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 该选单表存在团队空间中
     */
    @TableField(value = "spaceId")
    private Long spaceId;

    /**
     * 创建用户id
     */
    @TableField(value = "createdBy")
    private Long createdBy;

    /**
     * 选单标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 该选单状态，默认创建时为草稿
     */
    @TableField(value = "status")
    private String status;

    /**
     * 可选择图片数量限制
     */
    @TableField(value = "selectionLimit")
    private Integer selectionLimit;

    /**
     * 公开项目定位符，不是访问凭证
     */
    @TableField(value = "publicId")
    private String publicId;

    /**
     * 业务版本号，实际修改时递增
     */
    @TableField(value = "version")
    private Long version;

    /**
     *
     */
    @TableField(value = "createTime")
    private Date createTime;

    /**
     *
     */
    @TableField(value = "updateTime")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
