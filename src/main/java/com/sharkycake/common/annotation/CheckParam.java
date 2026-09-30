package com.sharkycake.common.annotation;


import org.apache.kafka.common.protocol.types.Field;

import java.lang.annotation.*;

/**
 * 定义一个只能放在参数上的运行时生效注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckParam {
    /**
     * 用来标记是否需要被检查
     */
    boolean ifCheck()  default true;
}
