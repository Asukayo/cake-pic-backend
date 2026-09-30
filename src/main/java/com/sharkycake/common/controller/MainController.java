package com.sharkycake.common.controller;

import com.sharkycake.common.annotation.CheckParam;
import com.sharkycake.common.annotation.StrVal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务状态 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "服务状态")
@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 检查 HTTP 服务状态。
     * 无需登录。data 为 ok；仅表示请求可达，不检查数据库、Redis、Kafka 或 COS。
     */
    @ApiOperation(value = "检查 HTTP 服务状态",
            notes = "无需登录。data 为 ok；仅表示请求可达，不检查数据库、Redis、Kafka 或 COS。")
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }

    @GetMapping("/checkAnnotation/${needTobeChecked}")
    @CheckParam(ifCheck = true)
    public BaseResponse<String> check(
            @StrVal(Min =  0, Max = 10)
            @PathVariable String needTobeChecked){
        return ResultUtils.success("check ok");
    }

}
