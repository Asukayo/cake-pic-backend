package com.sharkycake.user.auth;

import com.sharkycake.user.auth.annotation.AuthCheck;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.user.entity.User;
import com.sharkycake.user.enums.UserRoleEnum;
import com.sharkycake.user.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     * @return
     * @throws Throwable
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 获取当前方法所需要的权限
        String mustRole = authCheck.mustRole();
        // 2. 获取当前所有请求的上下文参数
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        // 转换为子类，获取当线程的request属性
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        //3.获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 获取当前方法所需的权限对应的Enum枚举类
        UserRoleEnum requiredRole = UserRoleEnum.getUserRoleEnum(mustRole);
        // 不需要权限，放行
        if (requiredRole == null) {
            return joinPoint.proceed();
        }
        // 以下为：必须有该权限才能通过
        // 获取当前用户的权限
        UserRoleEnum userRole = UserRoleEnum.getUserRoleEnum(loginUser.getUserRole());
        // 没有权限，拒绝
        ThrowUtils.throwIf(
                userRole==null,
                new BusinessException(ErrorCode.NO_AUTH_ERROR)
        );
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        ThrowUtils.throwIf(
                UserRoleEnum.ADMIN.equals(requiredRole) && !UserRoleEnum.ADMIN.equals(userRole),
                new BusinessException(ErrorCode.NO_AUTH_ERROR)
        );
        // 通过权限校验，放行
        return joinPoint.proceed();
    }

}
