package com.sharkycake.model.enums;

import cn.hutool.core.util.ObjUtil;
import com.sharkycake.exception.ThrowUtils;
import lombok.Data;
import lombok.Getter;

@Getter
public enum SpaceLevelEnum {

    COMMON("普通版",0,100,1024*1024*100L),
    PROFESSIONAL("会员版",1,1000,1024*1024*1000L),
    FLAGSHIP("旗舰版",2,10000,1024*1024*10000L);


    private final String text;

    private final int value;

    private final long maxCount;

    private final long maxSize;

    /**
     * @param text 文本
     * @param value 值
     * @param maxCount 最大图片数量
     * @param maxSize 最大图片总大小
     */
    SpaceLevelEnum(String text, int value, long maxCount, long maxSize) {
        this.text = text;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    /**
     * 根据value获取枚举值
     */
    public static SpaceLevelEnum getSpaceLevelEnumByValue(int value) {
        if (ObjUtil.isEmpty(value)) {return null;}
        for (SpaceLevelEnum spaceLevelEnum : SpaceLevelEnum.values()) {
            if (spaceLevelEnum.getValue() == value) {
                return spaceLevelEnum;
            }
        }
        return null;
    }

}
