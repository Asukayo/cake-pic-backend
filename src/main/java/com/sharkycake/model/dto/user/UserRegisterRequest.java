package com.sharkycake.model.dto.user;


import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册接口请求参数封装类
 */
@Data
public class UserRegisterRequest implements Serializable {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

}
