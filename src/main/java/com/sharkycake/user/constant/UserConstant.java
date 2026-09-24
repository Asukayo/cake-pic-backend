package com.sharkycake.user.constant;


/**
 * 统一声明用户相关的常量
 */

public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    String DEFAULT_USERNAME_PREFIX = "user_";

    String DEFAULT_AVATAR_URL = "https://cake-pic-1316699316.cos.ap-shanghai.myqcloud.com/public/1996871588537298945/2026-02-26_3lFxowASIoXasK2E.webp";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    // endregion
}
