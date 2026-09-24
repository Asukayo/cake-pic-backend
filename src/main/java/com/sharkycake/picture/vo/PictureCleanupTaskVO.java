package com.sharkycake.picture.vo;

import com.sharkycake.picture.entity.PictureCleanupTask;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
@ApiModel(description = "清理任务的管理视图；不返回存储桶、对象 key 或消息载荷")
public class PictureCleanupTaskVO implements Serializable {
    @ApiModelProperty("任务主键；Long 在响应中序列化为字符串")
    private Long id;
    @ApiModelProperty("任务事件 ID，也是人工重试接口使用的标识")
    private String eventId;
    @ApiModelProperty("已删除图片的 ID")
    private Long pictureId;
    @ApiModelProperty("发起删除的用户 ID；历史补建任务可为 null")
    private Long operatorId;
    @ApiModelProperty(value = "0 待处理、1 处理中、2 完成、3 待重试、4 等待引用释放、5 需人工处理", allowableValues = "0,1,2,3,4,5")
    private Integer taskStatus;
    @ApiModelProperty("创建时间")
    private Date createTime;
    @ApiModelProperty("最近状态更新时间")
    private Date updateTime;

    public static PictureCleanupTaskVO objToVo(PictureCleanupTask task) {
        if (task == null) return null;
        PictureCleanupTaskVO view = new PictureCleanupTaskVO();
        BeanUtils.copyProperties(task, view);
        return view;
    }

    private static final long serialVersionUID = 1L;
}
