package com.sharkycake.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.DeleteRequest;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.constant.SpaceUserPermissionConstant;
import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.manager.auth.annotation.SaSpaceCheckPermission;
import com.sharkycake.manager.auth.SpaceUserAuthManager;
import com.sharkycake.manager.auth.model.SpaceUserAuthConfig;
import com.sharkycake.model.enums.SpaceRoleEnum;
import com.sharkycake.model.dto.spaceuser.SpaceUserLeaveRequest;
import com.sharkycake.model.entity.Space;
import com.sharkycake.model.vo.SpacePermissionVO;
import com.sharkycake.service.SpaceService;
import io.swagger.annotations.ApiParam;
import com.sharkycake.model.dto.spaceuser.SpaceUserAddRequest;
import com.sharkycake.model.dto.spaceuser.SpaceUserEditRequest;
import com.sharkycake.model.dto.spaceuser.SpaceUserQueryRequest;
import com.sharkycake.model.entity.SpaceUser;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.vo.SpaceUserVO;
import com.sharkycake.service.SpaceUserService;
import com.sharkycake.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;

/**
 * 团队成员与权限 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "团队成员与权限")
@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 查询团队角色与权限配置。
     * 必须登录，无业务参数。返回 roles 和 permissions 配置；这是角色字典，不是当前用户授权结果。
     */
    @ApiOperation(value = "查询团队角色与权限配置",
            notes = "必须登录，无业务参数。返回 roles 和 permissions 配置；这是角色字典，不是当前用户授权结果。")
    @GetMapping("/roles")
    public BaseResponse<SpaceUserAuthConfig> listSpaceRoles(HttpServletRequest request) {
        userService.getLoginUser(request);
        return ResultUtils.success(SpaceUserAuthManager.SPACE_USER_AUTH_CONFIG);
    }

    /**
     * 查询我在空间中的权限。
     * 必须登录。spaceId 必填且大于0；按数据库最新用户身份和成员关系返回角色、permissionList、owner 与 can* 能力。非成员无权限，平台管理员不自动获得团队角色。
     */
    @ApiOperation(value = "查询我在空间中的权限",
            notes = "必须登录。spaceId 必填且大于0；按数据库最新用户身份和成员关系返回角色、permissionList、owner 与 can* 能力。非成员无权限，平台管理员不自动获得团队角色。")
    @GetMapping("/permissions")
    public BaseResponse<SpacePermissionVO> getMySpacePermissions(
            @ApiParam(value = "空间 ID，必须大于 0", required = true) @RequestParam("spaceId") Long spaceId,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        User currentUser = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN_ERROR);
        Space space = spaceService.getById(spaceId);
        return ResultUtils.success(spaceUserAuthManager.getPermissionView(space, currentUser));
    }

    /**
     * 退出我加入的团队。
     * 必须登录。JSON 必填 spaceId>0，仅删除当前用户的成员关系；创建者不可退出，私有空间不支持，重复退出或未加入返回40400。
     */
    @ApiOperation(value = "退出我加入的团队",
            notes = "必须登录。JSON 必填 spaceId>0，仅删除当前用户的成员关系；创建者不可退出，私有空间不支持，重复退出或未加入返回40400。")
    @PostMapping("/leave")
    public BaseResponse<Boolean> leaveTeam(@RequestBody SpaceUserLeaveRequest leaveRequest,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(leaveRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        spaceUserService.leaveTeam(leaveRequest.getSpaceId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 添加团队成员。
     * 需要 spaceUser:manage。JSON 必填 spaceId、userId；spaceRole为viewer/editor/admin，省略默认viewer；仅团队空间支持。返回成员关系ID；重复添加返回“该用户已加入团队”。
     */
    @ApiOperation(value = "添加团队成员",
            notes = "需要 spaceUser:manage。JSON 必填 spaceId、userId；spaceRole为viewer/editor/admin，省略默认viewer；仅团队空间支持。返回成员关系ID；重复添加返回“该用户已加入团队”。")
    @PostMapping("/add")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(id);
    }

    /**
     * 移除空间成员。
     * 需要 spaceUser:manage。JSON 必填 id>0，指成员关系ID而非用户ID；不允许移除空间创建者。
     */
    @ApiOperation(value = "移除空间成员",
            notes = "需要 spaceUser:manage。JSON 必填 id>0，指成员关系ID而非用户ID；不允许移除空间创建者。")
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();

        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        Space space = spaceService.getById(oldSpaceUser.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(Objects.equals(space.getUserId(), oldSpaceUser.getUserId()),
                ErrorCode.OPERATION_ERROR, "不能移除空间创建者");

        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询指定空间成员关系。
     * 需要 spaceUser:manage。JSON 必填 spaceId、userId，返回 SpaceUser；普通成员查询自身权限请使用 /spaceUser/permissions。
     */
    @ApiOperation(value = "查询指定空间成员关系",
            notes = "需要 spaceUser:manage。JSON 必填 spaceId、userId，返回 SpaceUser；普通成员查询自身权限请使用 /spaceUser/permissions。")
    @PostMapping("/get")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {

        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0 || userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);

        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    /**
     * 查询空间成员列表。
     * 需要 spaceUser:manage。请求体必填，spaceId必须大于0；可选userId、spaceRole、id。不分页，返回SpaceUserVO[]。
     */
    @ApiOperation(value = "查询空间成员列表",
            notes = "需要 spaceUser:manage。请求体必填，spaceId必须大于0；可选userId、spaceRole、id。不分页，返回SpaceUserVO[]。")
    @PostMapping("/list")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceUserQueryRequest.getSpaceId() == null || spaceUserQueryRequest.getSpaceId() <= 0,
                ErrorCode.PARAMS_ERROR, "spaceId必须大于0");
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList,request));
    }

    /**
     * 设置空间成员角色。
     * 需要 spaceUser:manage。JSON 必填成员关系id>0和spaceRole=viewer/editor/admin；创建者必须保留admin角色。返回布尔值，不修改用户的平台角色。
     */
    @ApiOperation(value = "设置空间成员角色",
            notes = "需要 spaceUser:manage。JSON 必填成员关系id>0和spaceRole=viewer/editor/admin；创建者必须保留admin角色。返回布尔值，不修改用户的平台角色。")
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest,
                                               HttpServletRequest request) {
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        ThrowUtils.throwIf(StrUtil.isBlank(spaceUser.getSpaceRole()), ErrorCode.PARAMS_ERROR, "请选择成员角色");

        spaceUserService.validSpaceUser(spaceUser, false);

        long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        Space space = spaceService.getById(oldSpaceUser.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(Objects.equals(space.getUserId(), oldSpaceUser.getUserId())
                        && !SpaceRoleEnum.ADMIN.getValue().equals(spaceUser.getSpaceRole()),
                ErrorCode.OPERATION_ERROR, "空间创建者必须保留管理员角色");

        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询我加入的团队。
     * 必须登录，无请求体。通过当前用户的成员关系返回 SpaceUserVO[]，包含 spaceRole、空间和用户展示信息。
     */
    @ApiOperation(value = "查询我加入的团队",
            notes = "必须登录，无请求体。通过当前用户的成员关系返回 SpaceUserVO[]，包含 spaceRole、空间和用户展示信息。")
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList,request));
    }
}
