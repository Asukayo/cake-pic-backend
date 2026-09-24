package com.sharkycake.picture.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "管理员分页查询清理任务；固定按 createTime、id 倒序，不接受任意排序字段")
public class PictureCleanupTaskQueryRequest implements Serializable {
    @ApiModelProperty(value = "当前页，从 1 开始", example = "1")
    private int current = 1;

    @ApiModelProperty(value = "每页条数，1–100，默认 10", example = "10")
    private int pageSize = 10;

    @ApiModelProperty(value = "事件 ID 精确匹配；省略表示不限，最长 64 字符", example = "cleanup-example-001")
    private String eventId;

    @ApiModelProperty(value = "图片 ID 精确匹配，传入时必须大于 0", example = "4001")
    private Long pictureId;

    @ApiModelProperty(value = "0 待处理、1 处理中、2 完成、3 待重试、4 等待引用释放、5 需人工处理", allowableValues = "0,1,2,3,4,5")
    private Integer taskStatus;

    private static final long serialVersionUID = 1L;
}
