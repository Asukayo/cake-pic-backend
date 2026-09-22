package com.sharkycake.model.dto.spaceuser;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "退出当前用户已加入的团队；不能指定其他用户")
public class SpaceUserLeaveRequest implements Serializable {
    @ApiModelProperty(value = "团队空间 ID，必须大于 0", required = true, example = "1001")
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
