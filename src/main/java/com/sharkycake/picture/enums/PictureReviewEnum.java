package com.sharkycake.picture.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum PictureReviewEnum {

    REVIEWING("Pending review",0),
    PASS("Approved",1),
    REJECT("Reject",2);

    private final String text;
    private final int value;

    PictureReviewEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据value获取枚举
     */
    public static PictureReviewEnum getEnumByValue(int value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 该方法返回一个包含该枚举中所有常量的数组，并且顺序与你在代码中声明的顺序一致。
        for (PictureReviewEnum e : PictureReviewEnum.values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        return null;
    }

}
