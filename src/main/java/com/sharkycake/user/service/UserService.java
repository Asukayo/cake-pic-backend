package com.sharkycake.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.user.dto.UserQueryRequest;
import com.sharkycake.user.entity.User;
import com.sharkycake.user.vo.LoginUserVO;
import com.sharkycake.user.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author shark
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-12-05 16:33:18
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);



    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取加密后的密码
     * @param password
     * @return
     */
    String getEncryptedPassword(String password);

    /**
     * 登录用户用户获取自身数据的脱敏操作
     * @param user
     * @return
     */
    LoginUserVO convert2LoginUserVO(User user);

    /**
     * 查询非本人用户信息脱敏操作
     * @param user
     * @return
     */
    UserVO convert2UserVO(User user);

    /**
     * 查询非本人用户信息列表脱敏操作
     * @param userList
     * @return
     */
    List<UserVO> convert2UserVOList(List<User> userList);

    /**
     * 用户查询请求的封装
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getCombinedWrapper(UserQueryRequest userQueryRequest);

    /**
     * 是否为管理员
     * @param user
     * @return
     */
    boolean isAdmin(User user);



}
