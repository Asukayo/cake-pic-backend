package com.sharkycake.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum SpaceTypeEnum {

    PRIVATE("私有空间",0),
    TEAM("团队空间",1);

    private final String name;
    private final int value;

    private SpaceTypeEnum(String name, int value) {
        this.name = name;
        this.value = value;
    }

    /**
     * 根据value获取枚举类
     */
    public static SpaceTypeEnum getEnumByValue(Integer value) {
        if (ObjUtil.isNull(value)) {
            return null;
        }
        for (SpaceTypeEnum e : SpaceTypeEnum.values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
