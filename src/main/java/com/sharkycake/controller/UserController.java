package com.sharkycake.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.annotation.AuthCheck;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.DeleteRequest;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.constant.UserConstant;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.model.dto.user.*;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.vo.UserVO;
import com.sharkycake.service.UserService;
import com.sharkycake.model.vo.LoginUserVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户与登录 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "用户与登录")
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 注册用户。
     * 无需登录。必填 userAccount（至少4字符）、userPassword 和 checkPassword（至少8字符且相同）；返回新用户ID。
     */
    @ApiOperation(value = "注册用户",
            notes = "无需登录。必填 userAccount（至少4字符）、userPassword 和 checkPassword（至少8字符且相同）；返回新用户ID。")
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 登录并建立会话。
     * 必填 userAccount、userPassword；返回 LoginUserVO 并建立 Spring Session 和空间鉴权会话。后续请求需携带 Cookie。
     */
    @ApiOperation(value = "登录并建立会话",
            notes = "必填 userAccount、userPassword；返回 LoginUserVO 并建立 Spring Session 和空间鉴权会话。后续请求需携带 Cookie。")
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 查询当前登录用户。
     * 必须登录，无业务参数。返回当前会话中的脱敏用户信息，userRole 表示平台角色。
     */
    @ApiOperation(value = "查询当前登录用户",
            notes = "必须登录，无业务参数。返回当前会话中的脱敏用户信息，userRole 表示平台角色。")
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.convert2LoginUserVO(loginUser));
    }

    /**
     * 退出当前会话。
     * 必须登录，无业务参数。当前实现移除 Spring Session 登录状态；返回布尔值，不承诺撤销所有空间会话。
     */
    @ApiOperation(value = "退出当前会话",
            notes = "必须登录，无业务参数。当前实现移除 Spring Session 登录状态；返回布尔值，不承诺撤销所有空间会话。")
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 管理员创建用户。
     * 仅平台管理员。请求为 UserAddRequest；返回新用户ID。使用服务端默认初始密码，DTO 不接受密码字段。
     */
    @ApiOperation(value = "管理员创建用户",
            notes = "仅平台管理员。请求为 UserAddRequest；返回新用户ID。使用服务端默认初始密码，DTO 不接受密码字段。")
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {

        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);

        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        final String DEFAULT_PASSWORD = "123456789";
        String encryptedPassword = userService.getEncryptedPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptedPassword);

        boolean save = userService.save(user);
        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR,"创建用户失败");
        return ResultUtils.success(user.getId());
    }

    /**
     * 管理员查询用户实体。
     * 仅平台管理员。查询参数 id 必填且大于0；返回 User 实体，普通页面应使用脱敏接口。
     */
    @ApiOperation(value = "管理员查询用户实体",
            notes = "仅平台管理员。查询参数 id 必填且大于0；返回 User 实体，普通页面应使用脱敏接口。")
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(@RequestParam("id") Long id) {

        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User byId = userService.getById(id);

        ThrowUtils.throwIf(byId == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(byId);
    }

    /**
     * 查询用户公开资料。
     * 查询参数 id 必填且大于0；返回 UserVO。此入口没有平台管理员注解，仅返回脱敏字段。
     */
    @ApiOperation(value = "查询用户公开资料",
            notes = "查询参数 id 必填且大于0；返回 UserVO。此入口没有平台管理员注解，仅返回脱敏字段。")
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(@RequestParam("id") Long id) {

        BaseResponse<User> userById = getUserById(id);
        User data = userById.getData();
        return ResultUtils.success(userService.convert2UserVO(data));
    }

    /**
     * 管理员删除用户。
     * 仅平台管理员。JSON 中 id 必填且大于0；返回数据库删除结果。
     */
    @ApiOperation(value = "管理员删除用户",
            notes = "仅平台管理员。JSON 中 id 必填且大于0；返回数据库删除结果。")
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(
                deleteRequest == null || deleteRequest.getId()<=0,
                ErrorCode.PARAMS_ERROR);
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 管理员更新用户。
     * 仅平台管理员。JSON 中 id 必填且大于0；可修改 UserUpdateRequest 声明的资料和平台角色字段。
     */
    @ApiOperation(value = "管理员更新用户",
            notes = "仅平台管理员。JSON 中 id 必填且大于0；可修改 UserUpdateRequest 声明的资料和平台角色字段。")
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {

        ThrowUtils.throwIf(userUpdateRequest == null || userUpdateRequest.getId()<=0
                , ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean b = userService.updateById(user);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR,"更新操作失败");
        return ResultUtils.success(b);
    }

    /**
     * 管理员分页查询用户。
     * 仅平台管理员。请求体必填，默认 current=1、pageSize=10；返回 Page<UserVO>，不返回密码。
     */
    @ApiOperation(value = "管理员分页查询用户",
            notes = "仅平台管理员。请求体必填，默认 current=1、pageSize=10；返回 Page<UserVO>，不返回密码。")
    @PostMapping("list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest queryRequest) {

        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);

        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        IPage<User> page =
                userService.page(
                        new Page<User>(current, pageSize),
                        userService.getCombinedWrapper(queryRequest));

        Page<UserVO> userVOPage = new Page<>(current, pageSize, page.getTotal());
        List<UserVO> collect = page.getRecords()
                .stream().map(userService::convert2UserVO).collect(Collectors.toList());
        userVOPage.setRecords(collect);
        return ResultUtils.success(userVOPage);

    }

}
