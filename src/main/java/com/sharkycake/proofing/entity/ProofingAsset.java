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
 * @TableName proofing_asset
 */
@TableName(value ="proofing_asset")
@Data
public class ProofingAsset implements Serializable {
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
     * 上传员工 ID；ZIP 记录任务发起者 ID
     */
    @TableField(value = "uploadedBy")
    private Long uploadedBy;

    /**
     * PREVIEW、FINAL、ZIP
     */
    @TableField(value = "kind")
    private String kind;

    /**
     * 
     */
    @TableField(value = "bucket")
    private String bucket;

    /**
     * 
     */
    @TableField(value = "objectKey")
    private String objectKey;

    /**
     * 实际文件字节数
     */
    @TableField(value = "sizeBytes")
    private Long sizeBytes;

    /**
     * 文件内容 SHA-256 摘要
     */
    @TableField(value = "sha256")
    private String sha256;

    /**
     * 图片宽度；ZIP 为空
     */
    @TableField(value = "width")
    private Integer width;

    /**
     * 图片高度；ZIP 为空
     */
    @TableField(value = "height")
    private Integer height;

    /**
     * 实际存储文件的媒体类型
     */
    @TableField(value = "contentType")
    private String contentType;

    /**
     * STAGING、READY、DELETE_PENDING
     */
    @TableField(value = "status")
    private String status;

    /**
     * STAGING 清理时间
     */
    @TableField(value = "expiresAt")
    private Date expiresAt;

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
        ProofingAsset other = (ProofingAsset) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getProjectId() == null ? other.getProjectId() == null : this.getProjectId().equals(other.getProjectId()))
            && (this.getUploadedBy() == null ? other.getUploadedBy() == null : this.getUploadedBy().equals(other.getUploadedBy()))
            && (this.getKind() == null ? other.getKind() == null : this.getKind().equals(other.getKind()))
            && (this.getBucket() == null ? other.getBucket() == null : this.getBucket().equals(other.getBucket()))
            && (this.getObjectKey() == null ? other.getObjectKey() == null : this.getObjectKey().equals(other.getObjectKey()))
            && (this.getSizeBytes() == null ? other.getSizeBytes() == null : this.getSizeBytes().equals(other.getSizeBytes()))
            && (this.getSha256() == null ? other.getSha256() == null : this.getSha256().equals(other.getSha256()))
            && (this.getWidth() == null ? other.getWidth() == null : this.getWidth().equals(other.getWidth()))
            && (this.getHeight() == null ? other.getHeight() == null : this.getHeight().equals(other.getHeight()))
            && (this.getContentType() == null ? other.getContentType() == null : this.getContentType().equals(other.getContentType()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getExpiresAt() == null ? other.getExpiresAt() == null : this.getExpiresAt().equals(other.getExpiresAt()))
            && (this.getCreateTime() == null ? other.getCreateTime() == null : this.getCreateTime().equals(other.getCreateTime()))
            && (this.getUpdateTime() == null ? other.getUpdateTime() == null : this.getUpdateTime().equals(other.getUpdateTime()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getProjectId() == null) ? 0 : getProjectId().hashCode());
        result = prime * result + ((getUploadedBy() == null) ? 0 : getUploadedBy().hashCode());
        result = prime * result + ((getKind() == null) ? 0 : getKind().hashCode());
        result = prime * result + ((getBucket() == null) ? 0 : getBucket().hashCode());
        result = prime * result + ((getObjectKey() == null) ? 0 : getObjectKey().hashCode());
        result = prime * result + ((getSizeBytes() == null) ? 0 : getSizeBytes().hashCode());
        result = prime * result + ((getSha256() == null) ? 0 : getSha256().hashCode());
        result = prime * result + ((getWidth() == null) ? 0 : getWidth().hashCode());
        result = prime * result + ((getHeight() == null) ? 0 : getHeight().hashCode());
        result = prime * result + ((getContentType() == null) ? 0 : getContentType().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getExpiresAt() == null) ? 0 : getExpiresAt().hashCode());
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
        sb.append(", uploadedBy=").append(uploadedBy);
        sb.append(", kind=").append(kind);
        sb.append(", bucket=").append(bucket);
        sb.append(", objectKey=").append(objectKey);
        sb.append(", sizeBytes=").append(sizeBytes);
        sb.append(", sha256=").append(sha256);
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", contentType=").append(contentType);
        sb.append(", status=").append(status);
        sb.append(", expiresAt=").append(expiresAt);
        sb.append(", createTime=").append(createTime);
        sb.append(", updateTime=").append(updateTime);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}