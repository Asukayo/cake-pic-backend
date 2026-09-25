package com.sharkycake.proofing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName proofing_item
 */
@TableName(value ="proofing_item")
@Data
public class ProofingItem implements Serializable {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属选单项目 ID
     */
    @TableField(value = "projectId")
    private Long projectId;

    /**
     * 预览文件 ID
     */
    @TableField(value = "previewAssetId")
    private Long previewAssetId;

    /**
     * 展示名称
     */
    @TableField(value = "displayName")
    private String displayName;

    /**
     * 展示顺序
     */
    @TableField(value = "sortOrder")
    private Integer sortOrder;

    /**
     * 是否选中
     */
    @TableField(value = "selected")
    private Integer selected;

    /**
     * 文字及可选矩形批注
     */
    @TableField(value = "annotation")
    private Object annotation;

    /**
     * 成片文件 ID
     */
    @TableField(value = "finalAssetId")
    private Long finalAssetId;

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

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        ProofingItem other = (ProofingItem) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getProjectId() == null ? other.getProjectId() == null : this.getProjectId().equals(other.getProjectId()))
            && (this.getPreviewAssetId() == null ? other.getPreviewAssetId() == null : this.getPreviewAssetId().equals(other.getPreviewAssetId()))
            && (this.getDisplayName() == null ? other.getDisplayName() == null : this.getDisplayName().equals(other.getDisplayName()))
            && (this.getSortOrder() == null ? other.getSortOrder() == null : this.getSortOrder().equals(other.getSortOrder()))
            && (this.getSelected() == null ? other.getSelected() == null : this.getSelected().equals(other.getSelected()))
            && (this.getAnnotation() == null ? other.getAnnotation() == null : this.getAnnotation().equals(other.getAnnotation()))
            && (this.getFinalAssetId() == null ? other.getFinalAssetId() == null : this.getFinalAssetId().equals(other.getFinalAssetId()))
            && (this.getCreateTime() == null ? other.getCreateTime() == null : this.getCreateTime().equals(other.getCreateTime()))
            && (this.getUpdateTime() == null ? other.getUpdateTime() == null : this.getUpdateTime().equals(other.getUpdateTime()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getProjectId() == null) ? 0 : getProjectId().hashCode());
        result = prime * result + ((getPreviewAssetId() == null) ? 0 : getPreviewAssetId().hashCode());
        result = prime * result + ((getDisplayName() == null) ? 0 : getDisplayName().hashCode());
        result = prime * result + ((getSortOrder() == null) ? 0 : getSortOrder().hashCode());
        result = prime * result + ((getSelected() == null) ? 0 : getSelected().hashCode());
        result = prime * result + ((getAnnotation() == null) ? 0 : getAnnotation().hashCode());
        result = prime * result + ((getFinalAssetId() == null) ? 0 : getFinalAssetId().hashCode());
        result = prime * result + ((getCreateTime() == null) ? 0 : getCreateTime().hashCode());
        result = prime * result + ((getUpdateTime() == null) ? 0 : getUpdateTime().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", projectId=").append(projectId);
        sb.append(", previewAssetId=").append(previewAssetId);
        sb.append(", displayName=").append(displayName);
        sb.append(", sortOrder=").append(sortOrder);
        sb.append(", selected=").append(selected);
        sb.append(", annotation=").append(annotation);
        sb.append(", finalAssetId=").append(finalAssetId);
        sb.append(", createTime=").append(createTime);
        sb.append(", updateTime=").append(updateTime);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}