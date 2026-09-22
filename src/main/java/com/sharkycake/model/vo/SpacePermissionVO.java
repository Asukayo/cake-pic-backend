package com.sharkycake.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(description = "当前用户在指定空间的角色权限和受所有者约束的操作能力；单张图片仍由业务接口鉴权")
public class SpacePermissionVO implements Serializable {
    @ApiModelProperty(value = "空间 ID；响应为十进制字符串", example = "1001")
    private Long spaceId;

    @ApiModelProperty(value = "空间类型：0 私有，1 团队", allowableValues = "0,1")
    private Integer spaceType;

    @ApiModelProperty(value = "团队成员角色；私有空间为 null", allowableValues = "viewer,editor,admin")
    private String spaceRole;

    @ApiModelProperty("是否为空间创建者")
    private boolean owner;

    @ApiModelProperty("角色授权的权限键；上传和批量编辑等还须结合 can* 字段")
    private List<String> permissionList;

    @ApiModelProperty("是否可新增图片到该空间；替换已有图片还需验证图片上传者")
    private boolean canUpload;

    @ApiModelProperty("是否可批量编辑该空间的图片")
    private boolean canBatchEdit;

    @ApiModelProperty("是否可查看该指定空间的统计")
    private boolean canAnalyze;

    @ApiModelProperty("是否可在该空间按颜色搜索")
    private boolean canSearchByColor;

    @ApiModelProperty("是否可退出团队；创建者不可退出")
    private boolean canLeave;

    private static final long serialVersionUID = 1L;
}
