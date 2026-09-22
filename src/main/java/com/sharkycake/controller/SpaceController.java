package com.sharkycake.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sharkycake.annotation.AuthCheck;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.DeleteRequest;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.constant.SpaceLevel;
import com.sharkycake.constant.UserConstant;
import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.model.dto.space.SpaceAddRequest;
import com.sharkycake.model.dto.space.SpaceEditRequest;
import com.sharkycake.model.dto.space.SpaceQueryRequest;
import com.sharkycake.model.dto.space.SpaceUpdateRequest;
import com.sharkycake.model.entity.Space;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.enums.SpaceLevelEnum;
import com.sharkycake.model.enums.SpaceTypeEnum;
import io.swagger.annotations.ApiParam;
import com.sharkycake.model.vo.SpaceVO;
import com.sharkycake.service.SpaceService;
import com.sharkycake.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间管理 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "空间管理")
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;
    @Autowired
    private UserService userService;

    /**
     * 查询我创建的空间。
     * 必须登录。可选 spaceType=0或1；用户ID从会话取得，不能指定其他用户。按创建时间和ID倒序返回 SpaceVO[]；加入他人的团队另查 /spaceUser/list/my。
     */
    @ApiOperation(value = "查询我创建的空间",
            notes = "必须登录。可选 spaceType=0或1；用户ID从会话取得，不能指定其他用户。按创建时间和ID倒序返回 SpaceVO[]；加入他人的团队另查 /spaceUser/list/my。")
    @GetMapping("/list/my")
    public BaseResponse<List<SpaceVO>> listMySpaces(
            @ApiParam(value = "可选空间类型：0 私有，1 团队", allowableValues = "0,1")
            @RequestParam(value = "spaceType", required = false) Integer spaceType,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(spaceType != null && SpaceTypeEnum.getEnumByValue(spaceType) == null,
                ErrorCode.PARAMS_ERROR, "spaceType必须为0或1");
        List<Space> spaces = spaceService.list(new QueryWrapper<Space>()
                .eq("userId", loginUser.getId())
                .eq(spaceType != null, "spaceType", spaceType)
                .orderByDesc("createTime", "id"));
        return ResultUtils.success(spaces.stream().map(SpaceVO::objToVo).collect(Collectors.toList()));
    }

    /**
     * 查询空间级别与额度。
     * 无需登录，无业务参数。返回级别、名称、最大图片张数和最大字节数；Long 在 JSON 中为字符串。
     */
    @ApiOperation(value = "查询空间级别与额度",
            notes = "无需登录，无业务参数。返回级别、名称、最大图片张数和最大字节数；Long 在 JSON 中为字符串。")
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

    /**
     * 管理员更新空间配置。
     * 仅平台管理员。id 必填且大于0；可修改名称、级别和额度，参数以 SpaceUpdateRequest 为准。
     */
    @ApiOperation(value = "管理员更新空间配置",
            notes = "仅平台管理员。id 必填且大于0；可修改名称、级别和额度，参数以 SpaceUpdateRequest 为准。")
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        ThrowUtils.throwIf(spaceUpdateRequest==null || spaceUpdateRequest.getId() <=0 , ErrorCode.PARAMS_ERROR);
        boolean b = spaceService.updateSpace(spaceUpdateRequest);
        return ResultUtils.success(b);
    }

    /**
     * 空间所有者编辑空间。
     * 必须登录且为空间创建者。id 必填且大于0；返回编辑是否成功，不代表登录操作。
     */
    @ApiOperation(value = "空间所有者编辑空间",
            notes = "必须登录且为空间创建者。id 必填且大于0；返回编辑是否成功，不代表登录操作。")
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceEditRequest==null || spaceEditRequest.getId() <= 0 , ErrorCode.PARAMS_ERROR);
        boolean b = spaceService.editSpace(spaceEditRequest,request);
        return ResultUtils.success(b);
    }

    /**
     * 删除空间记录。
     * 必须登录且为空间创建者或平台管理员。DELETE 的 JSON 请求体必填 id；当前仅删除空间记录，不承诺级联清理图片、COS 或成员关系。
     */
    @ApiOperation(value = "删除空间记录",
            notes = "必须登录且为空间创建者或平台管理员。DELETE 的 JSON 请求体必填 id；当前仅删除空间记录，不承诺级联清理图片、COS 或成员关系。")
    @DeleteMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest==null || deleteRequest.getId() <= 0 , ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Space byId = spaceService.getById(deleteRequest.getId());
        if (byId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        ThrowUtils.
                throwIf(!ObjUtil.equal(byId.getUserId(),loginUser.getId()) &&(!userService.isAdmin(loginUser)),
                        ErrorCode.NO_AUTH_ERROR);
        boolean b = spaceService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!b,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(b);
    }

    /**
     * 创建私有或团队空间。
     * 必须登录。spaceType=0私有、1团队；普通用户仅可创建普通版，每人每类最多一个。省略spaceType默认私有空间；创建团队时在同一事务内添加创建者的admin成员关系。
     */
    @ApiOperation(value = "创建私有或团队空间",
            notes = "必须登录。spaceType=0私有、1团队；普通用户仅可创建普通版，每人每类最多一个。省略spaceType默认私有空间；创建团队时在同一事务内添加创建者的admin成员关系。")
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        long l = spaceService.addSpace(spaceAddRequest, request);
        return ResultUtils.success(l);
    }

    /**
     * 管理员查询空间实体。
     * 仅平台管理员。查询参数 id 必填且大于0；返回 Space，未找到时当前实现 data 可能为 null。
     */
    @ApiOperation(value = "管理员查询空间实体",
            notes = "仅平台管理员。查询参数 id 必填且大于0；返回 Space，未找到时当前实现 data 可能为 null。")
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpace(@RequestParam Long id) {

        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        return ResultUtils.success(space);
    }

    /**
     * 查询空间展示信息。
     * 查询参数 id 必填且大于0。返回 SpaceVO 及创建用户信息；当前入口没有成员访问限制，不包含图片内容或权限列表。
     */
    @ApiOperation(value = "查询空间展示信息",
            notes = "查询参数 id 必填且大于0。返回 SpaceVO 及创建用户信息；当前入口没有成员访问限制，不包含图片内容或权限列表。")
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVo(@RequestParam Long id, HttpServletRequest request) {

        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
        return ResultUtils.success(spaceVO);
    }

    /**
     * 管理员分页查询空间实体。
     * 仅平台管理员。请求体必填，默认 current=1、pageSize=10；按 SpaceQueryRequest 查询，返回 Page<Space>。
     */
    @ApiOperation(value = "管理员分页查询空间实体",
            notes = "仅平台管理员。请求体必填，默认 current=1、pageSize=10；按 SpaceQueryRequest 查询，返回 Page<Space>。")
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listPage(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {

        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<Space> spacePage = spaceService.listSpacePage(spaceQueryRequest, request);
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页查询空间展示信息。
     * 请求体必填，默认 current=1、pageSize=10。当前入口不自动限定登录用户；我的空间页面请使用 /space/list/my。
     */
    @ApiOperation(value = "分页查询空间展示信息",
            notes = "请求体必填，默认 current=1、pageSize=10。当前入口不自动限定登录用户；我的空间页面请使用 /space/list/my。")
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listPageVo(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<Space> spacePage = spaceService.listSpacePage(spaceQueryRequest, request);
        Page<SpaceVO> listSpaceVO = spaceService.getListSpaceVO(spacePage);
        return ResultUtils.success(listSpaceVO);
    }

}
