package com.sharkycake.user.service.impl;

import ch.qos.logback.classic.spi.EventArgUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.generator.UUIDGenerator;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.sharkycake.user.constant.UserConstant;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.space.auth.StpKit;
import com.sharkycake.user.dto.UserQueryRequest;
import com.sharkycake.user.entity.User;
import com.sharkycake.user.enums.UserRoleEnum;
import com.sharkycake.user.vo.LoginUserVO;
import com.sharkycake.user.vo.UserVO;
import com.sharkycake.user.service.UserService;
import com.sharkycake.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.sharkycake.user.constant.UserConstant.USER_LOGIN_STATE;

/**
* @author shark
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-12-05 16:33:18
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1.校验
        // 1.1 判空
        ThrowUtils.throwIf(
                StrUtil.hasBlank(userAccount,userPassword,checkPassword),
                new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空")
        );
        // 1.2.判断账号是否符合规范
        ThrowUtils.throwIf(
                userAccount.length() < 4,
                new BusinessException(ErrorCode.PARAMS_ERROR,"账号长度过短")
        );
        // 1.3.判断密码长度是否合规
        ThrowUtils.throwIf(
                userPassword.length()<8 || checkPassword.length()<8,
                new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度不符合规范")
        );
        // 1.4.判断两次密码是否一致
        ThrowUtils.throwIf(
                !userPassword.equals(checkPassword),
                new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码不一致")
        );
        // 2.判断该用户名是否被注册过
        long count = this.count(
                new QueryWrapper<User>().eq("userAccount", userAccount));
        // 2.1 用户已存在
        ThrowUtils.throwIf(count>0,
                new BusinessException(ErrorCode.OPERATION_ERROR,"该用户已存在"));

        //2.2 用户不存在
        // 对密码进行加密操作
        String ePassword = getEncryptedPassword(userPassword);
        // 将用户数据存入数据库
        User newUser = new User();
        newUser.setUserAccount(userAccount);
        newUser.setUserPassword(ePassword);
        // 设置用户默认属性
        UUIDGenerator uuidGenerator = new UUIDGenerator();
        String next = uuidGenerator.next();
        newUser.setUserName(UserConstant.DEFAULT_USERNAME_PREFIX + next);
        newUser.setUserAvatar(UserConstant.DEFAULT_AVATAR_URL);
        newUser.setUserRole(UserRoleEnum.USER.getValue());
        newUser.setUserProfile("这个用户很懒，还没有写简介");
        boolean save = this.save(newUser);
        ThrowUtils.throwIf(!save,new BusinessException(ErrorCode.SYSTEM_ERROR,"数据库错误"));
        // 完成插入或者更新操作后id值默认回传
        return newUser.getId();
    }

    /**
     * 用户登录
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request request参数，用于将用户登录态存放到session中
     * @return 脱敏后的登录用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1.校验
        ThrowUtils.throwIf(
                StrUtil.hasBlank(userAccount,userPassword),
                new BusinessException(ErrorCode.PARAMS_ERROR,"用户名或者密码不可为空")
        );
        ThrowUtils.throwIf(userAccount.length()<4,
                new BusinessException(ErrorCode.PARAMS_ERROR,"用户名格式不正确"));
        ThrowUtils.throwIf(userPassword.length()<8,
                new BusinessException(ErrorCode.PARAMS_ERROR,"密码格式不正确"));
        //2.用户登录判断
        QueryWrapper<User> loginWrapper = new QueryWrapper<User>().eq("userAccount", userAccount)
                .eq("userPassword", getEncryptedPassword(userPassword));
        User loginUser = this.getOne(loginWrapper);
        //2.1 登陆失败
        ThrowUtils.throwIf(loginUser==null,
                new BusinessException(ErrorCode.PARAMS_ERROR,"用户名不存在或者密码错误"));
        // 2.2 登录成功
        // 用户数据脱敏
        LoginUserVO loginUserVO = convert2LoginUserVO(loginUser);
        // 将未脱敏数据数据存入到redis中
        request.getSession().setAttribute(USER_LOGIN_STATE, loginUser);
        // 记录用户到Sa-token,用于空间鉴权时使用，注意保证该用户信息与Spring Session中的信息过期时间一致
        StpKit.SPACE.login(loginUser.getId());
        StpKit.SPACE.getSession().set(USER_LOGIN_STATE, loginUser);
        // 返回给前端脱敏后的数据
        return loginUserVO;
    }

    /**
     * 获取当前登录用户信息，用户信息经过脱敏操作
     * 但存储在redis中的数据可能过期
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        User currentUser =
                (User)request.getSession().getAttribute(USER_LOGIN_STATE);
        ThrowUtils.throwIf(
                currentUser==null || currentUser.getId()==null,
                new BusinessException(ErrorCode.NOT_LOGIN_ERROR)
        );
        // TODO可选操作，从数据库查询最新用户信息
        return currentUser;
    }

    /**
     * 用户注销接口
     * 移除用户登录状态
     * @param request
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        ThrowUtils.throwIf(userObj==null,
                new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"未登录"));
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }


    /**
     * // 针对用户密码加盐操作进行加密
     * @param password 未加密密码
     * @return  已加密密码
     */
    public String getEncryptedPassword(String password) {
        // 盐值，混淆密码
        final String SALT = "Sharkycake";
        return DigestUtils.md5DigestAsHex((password + SALT).getBytes());
    }

    /**
     * 对用户数据进行脱敏操作
     * @param user 未脱敏用户数据
     * @return  已脱敏用户数据(暂时去除了密码)
     */
    public LoginUserVO convert2LoginUserVO(User user) {
        // 判空，防止空指针异常
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user,loginUserVO);
        return loginUserVO;
    }

    /**
     * 对查询非本人用户信息脱敏操作
     * @param user
     * @return
     */
    @Override
    public UserVO convert2UserVO(User user) {
        // 判空操作
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user,userVO);
        return userVO;
    }

    /**
     * 对查询非本人用户信息列表的脱敏操作
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> convert2UserVOList(List<User> userList) {
        // 如果传入的userList为空
        if (CollUtil.isEmpty(userList)) {return new ArrayList<>();}
        // userList为非空的情况
        List<UserVO> collect = userList
                .stream()
                .map(this::convert2UserVO)
                .collect(Collectors.toList());
        return collect;
    }

    /**
     * 对用户查询请求的封装
     * @param userQueryRequest
     * @return 返回封装好的QueryWrapper
     */
    @Override
    public QueryWrapper<User> getCombinedWrapper(UserQueryRequest userQueryRequest) {
        // 1. 参数校验
        ThrowUtils.throwIf(
                userQueryRequest==null,
                new BusinessException(ErrorCode.PARAMS_ERROR,"userQueryWrapper请求参数为空")
        );
        // 构建参数
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        // id不为空的情况下,两个需要精准查询的字段
        userQueryWrapper.eq(ObjUtil.isNotNull(id) && id > 0 ,"id", id);
        userQueryWrapper.eq(StrUtil.isNotBlank(userRole),"userRole", userRole);
        // 模糊查询的字段
        userQueryWrapper.like(StrUtil.isNotBlank(userName),"userName", userName);
        userQueryWrapper.like(StrUtil.isNotBlank(userAccount),"userAccount", userAccount);
        userQueryWrapper.like(StrUtil.isNotBlank(userProfile),"userProfile", userProfile);
        // 排序字段,三个参数：第一个是是否需要拼接排序，第二个判断是否为升序，不是就是用降序，第三个是排序字段
        userQueryWrapper.orderBy(StrUtil.isNotBlank(sortField),sortOrder.equals("ascend"),sortField);

        return userQueryWrapper;
    }

    /**
     * 用于判断用户是否为管理员的方法
     * @param user
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        return user !=null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }


}




