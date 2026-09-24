package com.sharkycake.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 图片 COS 文件清理任务
 * @TableName picture_cleanup_task
 */
@TableName(value ="picture_cleanup_task")
@Data
public class PictureCleanupTask implements Serializable {
    /**
     * 任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务事件唯一标识，由应用生成，重试时保持不变
     */
    @TableField(value = "eventId")
    private String eventId;

    /**
     * 关联图片 ID，图片记录删除后仍保留
     */
    @TableField(value = "pictureId")
    private Long pictureId;

    /**
     * 操作用户 ID，历史补偿无法确定操作人时为空
     */
    @TableField(value = "operatorId")
    private Long operatorId;

    /**
     * COS 存储桶，历史数据缺失时需补齐
     */
    @TableField(value = "storageBucket")
    private String storageBucket;

    /**
     * 原图对象 key，历史数据缺失时需补齐
     */
    @TableField(value = "originalKey")
    private String originalKey;

    /**
     * 压缩图对象 key，无压缩图时为空
     */
    @TableField(value = "compressedKey")
    private String compressedKey;

    /**
     * 缩略图对象 key，可能与压缩图相同
     */
    @TableField(value = "thumbnailKey")
    private String thumbnailKey;

    /**
     * 0-待处理 1-处理中 2-已完成 3-待重试 4-等待引用释放 5-需人工处理
     */
    @TableField(value = "taskStatus")
    private Integer taskStatus;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "updateTime")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}