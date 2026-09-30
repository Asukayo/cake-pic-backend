package com.sharkycake.common.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义具体的动态校验规则
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface StrVal {
    /**
     * 定义长度校验规则
     */
    int Min() default 0;
    int Max() default Integer.MAX_VALUE;


}
