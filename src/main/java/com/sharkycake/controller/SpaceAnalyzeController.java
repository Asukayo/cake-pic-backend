package com.sharkycake.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.model.dto.space.analyze.*;
import com.sharkycake.model.entity.Space;
import com.sharkycake.model.vo.space.analyze.*;
import com.sharkycake.model.entity.User;
import com.sharkycake.service.SpaceAnalyzeService;
import com.sharkycake.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间统计 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "空间统计")
@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 统计空间容量与图片数量。
     * 请求体必填且必须登录。指定 spaceId 仅所有者可看；queryPublic/queryAll 仅平台管理员可用，queryAll优先。大小为字节，数量为张，Ratio 已是百分数；公共/全部范围上限与比例为 null。
     */
    @ApiOperation(value = "统计空间容量与图片数量",
            notes = "请求体必填且必须登录。指定 spaceId 仅所有者可看；queryPublic/queryAll 仅平台管理员可用，queryAll优先。大小为字节，数量为张，Ratio 已是百分数；公共/全部范围上限与比例为 null。")
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(
            @RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }

    /**
     * 按分类统计图片。
     * 请求体必填且必须登录。指定空间仅所有者，公共/全部范围仅平台管理员；返回 category、count（张）、totalSize（字节），不额外筛审核状态。
     */
    @ApiOperation(value = "按分类统计图片",
            notes = "请求体必填且必须登录。指定空间仅所有者，公共/全部范围仅平台管理员；返回 category、count（张）、totalSize（字节），不额外筛审核状态。")
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceCategoryAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 统计标签使用次数。
     * 请求体必填且必须登录。范围权限同 usage；按标签出现次数降序，count 不是去重图片数。
     */
    @ApiOperation(value = "统计标签使用次数",
            notes = "请求体必填且必须登录。范围权限同 usage；按标签出现次数降序，count 不是去重图片数。")
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(@RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceTagAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 统计图片大小分布。
     * 请求体必填且必须登录。范围权限同 usage；按1024计算KB/MB，固定4桶，标签 >1MB 实际包含恰好1MiB，count 为张数。
     */
    @ApiOperation(value = "统计图片大小分布",
            notes = "请求体必填且必须登录。范围权限同 usage；按1024计算KB/MB，固定4桶，标签 >1MB 实际包含恰好1MiB，count 为张数。")
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(@RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceSizeAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 按时间统计用户上传量。
     * 请求体必填且必须登录。范围权限同 usage；timeDimension 必传 day/week/month，可选 userId。按 createTime 分组并升序，week 使用 MySQL YEARWEEK，缺失日期不补0；无时间范围参数。
     */
    @ApiOperation(value = "按时间统计用户上传量",
            notes = "请求体必填且必须登录。范围权限同 usage；timeDimension 必传 day/week/month，可选 userId。按 createTime 分组并升序，week 使用 MySQL YEARWEEK，缺失日期不补0；无时间范围参数。")
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<SpaceUserAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 查询空间存储用量排行。
     * 仅平台管理员。请求体必填，topN 省略默认10；当前后端没有正数/上限校验。按 totalSize 字节数降序，实际只查询 id、spaceName、userId、totalSize。
     */
    @ApiOperation(value = "查询空间存储用量排行",
            notes = "仅平台管理员。请求体必填，topN 省略默认10；当前后端没有正数/上限校验。按 totalSize 字节数降序，实际只查询 id、spaceName、userId、totalSize。")
    @PostMapping("/rank")
    public BaseResponse<List<Space>> getSpaceRankAnalyze(@RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<Space> resultList = spaceAnalyzeService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

}
