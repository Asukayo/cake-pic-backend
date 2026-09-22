package com.sharkycake.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户权限校验注解
 * 配合AOP实现用户全歼晓燕
 */
//使用@Target可以定义Annotation能够被应用于源码的哪些位置：
// 类或接口：ElementType.TYPE；
//字段：ElementType.FIELD；
//方法：ElementType.METHOD；
//构造方法：ElementType.CONSTRUCTOR；
//方法参数：ElementType.PARAMETER。
@Target(ElementType.METHOD)
// 另一个重要的元注解@Retention定义了Annotation的生命周期：
// 仅编译期：RetentionPolicy.SOURCE；
//仅class文件：RetentionPolicy.CLASS；
//运行期：RetentionPolicy.RUNTIME。
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     * 注解的参数类似无参数方法，可以用default设定一个默认值（强烈推荐）。
     */
    String mustRole() default "";
}
