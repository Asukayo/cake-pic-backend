package com.sharkycake.user.enums;

import cn.hutool.core.util.ObjUtil;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import lombok.Getter;

@Getter
public enum UserRoleEnum {

    USER("用户","user"),
    ADMIN("管理员","admin");

    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据value获取枚举值
     * 如果枚举值特别多，也可以用HashMap缓存所有的枚举值来加速查找，而不是遍历列表
     * @param value
     * @return
     */
    public static UserRoleEnum getUserRoleEnum(String value) {
        // 健壮性判断
        if(ObjUtil.isEmpty(value)) return null;
        // 进行查询
        // 循环遍历枚举类中的每个枚举值，查找到之后进行返回
        for (UserRoleEnum e : UserRoleEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        // 没有找到，返回null
        return null;
    }
}
